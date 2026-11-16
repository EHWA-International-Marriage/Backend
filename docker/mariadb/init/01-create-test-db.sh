#!/bin/sh
# 테스트 프로필(application-test.yaml)용 DB. 컨테이너 최초 기동(빈 볼륨) 때만 실행된다.
mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" <<SQL
CREATE DATABASE IF NOT EXISTS \`${MARIADB_DATABASE}_test\`;
GRANT ALL PRIVILEGES ON \`${MARIADB_DATABASE}_test\`.* TO '${MARIADB_USER}'@'%';
FLUSH PRIVILEGES;
SQL
