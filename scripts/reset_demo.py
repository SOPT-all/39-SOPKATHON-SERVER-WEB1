"""Operate on the single deployed demo app from its EC2 host (Python 3.8+)."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
from urllib.parse import urlsplit
from urllib.request import urlopen

TABLES = ("record", "story", "context", "chat")
SEED = Path("src/main/resources/data.sql")
APP = "sopkathon"
CLIENT = "mysql:8.4"


def run(*args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)


def seed_tables(sql):
    # Only load checked-in literal INSERTs into empty temporary tables.
    # The old CHARACTER UPDATE/DELETE migrations operate on existing rows only.
    token = r"'(?:[^'\\]|'')*'"
    statement = re.compile(r"(?:\s+|--[^\n]*(?:\n|$))*((?:" + token + r"|[^;'\\])+);", re.S)
    chunks = []
    found = set()
    pos = 0
    while sql[pos:].strip():
        match = statement.match(sql, pos)
        if not match:
            raise ValueError("지원하지 않는 시드 SQL")
        body = match[1].strip()
        pos = match.end()
        if re.fullmatch(r"(?:DELETE FROM context WHERE |UPDATE context SET ).+", body, re.S):
            # Only historical data migrations preceding the context inserts may be skipped.
            if "context" in found:
                raise ValueError("초기 데이터 변경 SQL을 검토하세요.")
            continue
        insert = re.fullmatch(r"INSERT INTO (\w+) (\([\w, ]+\)) VALUES\s+(.+)\s+ON DUPLICATE KEY UPDATE (\w+) = \4", body, re.S)
        if not insert or insert[1] not in TABLES:
            raise ValueError("지원하지 않는 시드 구문")
        table, columns, rows = insert.group(1, 2, 3)
        literal = "(?:" + token + r"|-?\d+|NULL)"
        row = r"\(\s*" + literal + r"(?:\s*,\s*" + literal + r")*\s*\)"
        if not re.fullmatch(row + r"(?:\s*,\s*" + row + r")*", rows.strip(), re.S):
            raise ValueError("시드는 리터럴 행만 지원합니다.")
        chunks.append(f"INSERT INTO expected_{table} {columns} VALUES {rows};")
        found.add(table)
    if found != set(TABLES):
        raise ValueError("네 테이블의 시드가 모두 필요합니다.")
    return chunks


def reset_sql(seed, columns, apply):
    stmts = [f"CREATE TEMPORARY TABLE expected_{t} LIKE `{t}`;" for t in TABLES]
    stmts.extend(seed_tables(seed))
    for t in TABLES:
        stmts.append(f"SELECT '{t} current/seed', (SELECT COUNT(*) FROM `{t}`), COUNT(*) FROM expected_{t};")
    stmts.append("SELECT 'removed story', story_id FROM story WHERE story_id NOT IN (SELECT story_id FROM expected_story);")
    if not apply:
        return "\n".join(stmts)
    stmts += ["CREATE TEMPORARY TABLE guard_reset (id INT PRIMARY KEY);",
              "INSERT INTO guard_reset VALUES (1);", "START TRANSACTION;"]
    for t in reversed(TABLES):
        stmts.append(f"DELETE FROM `{t}`;")
    for t in TABLES:
        stmts.append(f"INSERT INTO `{t}` SELECT * FROM expected_{t};")
        equal = " AND ".join(f"BINARY a.`{col}` <=> BINARY e.`{col}`" for col in columns[t])
        # A mismatch raises a duplicate-key error; batch mysql exits before COMMIT.
        stmts.append(f"""INSERT INTO guard_reset SELECT 1 WHERE
            (SELECT COUNT(*) FROM `{t}`) <> (SELECT COUNT(*) FROM expected_{t})
            ;
            INSERT INTO guard_reset SELECT 1 WHERE EXISTS (SELECT 1 FROM expected_{t} e WHERE NOT EXISTS
            (SELECT 1 FROM `{t}` a WHERE {equal}));""")
    return "\n".join(stmts + ["COMMIT;"])


def health():
    for attempt in range(40):
        try:
            for path in ["stories", "stories/1", "stories/1/messages"]:
                with urlopen("http://127.0.0.1:8080/api/v1/" + path, timeout=3) as response:
                    if json.load(response)["code"] != "SUCCESS":
                        raise ValueError("API 응답 오류")
            return
        except (OSError, ValueError, KeyError):
            if attempt == 39:
                raise RuntimeError("앱 재기동 후 조회 실패")
            time.sleep(2)


def main():
    mode = os.environ["MODE"]
    info = json.loads(run("docker", "inspect", APP, capture_output=True).stdout)[0]
    if mode not in ("preview", "reset") or not info["State"]["Running"] or info["Config"]["Image"] != os.environ["RELEASE_IMAGE"].lower():
        raise ValueError("실행 중인 앱과 선택한 배포 SHA를 확인하세요.")
    env = dict(item.split("=", 1) for item in info["Config"]["Env"])
    if not env["DB_URL"].startswith("jdbc:mysql://"):
        raise ValueError("MySQL JDBC URL이 필요합니다.")
    db = urlsplit(env["DB_URL"][5:])
    name = db.path[1:]
    target = f"{db.hostname}:{db.port or 3306}/{name}"
    if not re.fullmatch(r"[A-Za-z0-9_]+", name) or target != os.environ["DB_TARGET"]:
        raise ValueError("시연 DB 대상 불일치")
    print(f"{mode}: {target}, {info['Config']['Image']}", flush=True)
    net = info["HostConfig"]["NetworkMode"]
    client = ["docker", "run", "--rm", "-i", "--network", net, "-e", "MYSQL_PWD", CLIENT]
    auth = ["--host", db.hostname, "--port", str(db.port or 3306), "--user", env["DB_USERNAME"],
            "--protocol=TCP", "--default-character-set=utf8mb4"]
    proc_env = dict(os.environ, MYSQL_PWD=env["DB_PASSWORD"])

    def query(sql):
        result = subprocess.run(client + ["mysql", *auth, "-BN", name], input=sql, text=True,
                                env=proc_env, capture_output=True)
        if result.returncode:
            code = re.search(r"ERROR \d+", result.stderr)
            raise RuntimeError("MySQL 작업 실패: " + (code[0] if code else "연결 또는 클라이언트 오류"))
        return result.stdout.strip()

    if query("SELECT DATABASE();") != name:
        raise ValueError("실제 DB 이름 불일치")
    engines = query("SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE';")
    if set(engines.splitlines()) != {f"{t}\tInnoDB" for t in TABLES}:
        raise ValueError("예상한 네 InnoDB 테이블과 실제 DB가 다릅니다.")
    cols = query("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY ORDINAL_POSITION;")
    columns = {t: [] for t in TABLES}
    for entry in cols.splitlines():
        table, col = entry.split("\t")
        if table in columns:
            if not re.fullmatch(r"\w+", col):
                raise ValueError("지원하지 않는 컬럼 이름")
            columns[table].append(col)
    sql = reset_sql(SEED.read_text(encoding="utf-8"), columns, mode == "reset")
    if mode == "preview":
        print(query(sql))
        return

    backups = Path.home() / "sopkathon-backups"
    backups.mkdir(mode=0o700, exist_ok=True)
    try:
        run("docker", "stop", APP)
        with tempfile.NamedTemporaryFile(dir=backups, suffix=".partial", delete=False, mode="w") as out:
            out.write("START TRANSACTION;\n" + "".join(f"DELETE FROM `{t}`;\n" for t in reversed(TABLES)))
            out.flush()
            run(*client, "mysqldump", *auth, "--single-transaction", "--no-create-info",
                "--skip-triggers", "--skip-add-locks", "--skip-disable-keys", "--set-gtid-purged=OFF",
                "--column-statistics=0", "--no-tablespaces", name, *TABLES, env=proc_env, stdout=out)
            out.write("\nCOMMIT;\n")
        backup = Path(out.name).with_suffix(".sql")
        os.rename(out.name, backup)
        print(f"복구용 백업: {backup}", flush=True)
        print(query(sql))
    finally:
        run("docker", "start", APP)
        health()


if __name__ == "__main__":
    try:
        main()
    except (KeyError, ValueError, RuntimeError, subprocess.CalledProcessError) as exc:
        sys.exit(str(exc))
