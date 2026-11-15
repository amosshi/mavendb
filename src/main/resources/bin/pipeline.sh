#!/bin/sh
#
# Load the maven index into one or all databases.
# Each run stops all database containers, then refreshes only the one it loads.
#
# Usage:
#   ./pipeline.sh [ALL|mysql|psql|mongodb|sqlite]
#
#   ALL      Run for every database type, one after the other (default)
#   <dbtype> Run for that database type only
#
# Example:
#   ./pipeline.sh
#   ./pipeline.sh psql
#

DB_TYPES="mysql psql mongodb sqlite"

# Stop and remove every database container: the machine does not have enough RAM to run them all.
# Their volumes become unused, and are deleted by the volume prune of compose-<db_type>.sh.
stop_all_databases() {
    for compose_file in compose-mongodb.yml compose-mysql.yml compose-psql.yml; do
        sudo docker compose -f "$compose_file" down
    done
}

# Stop all databases, refresh only the given one, then load the maven index into it.
# Usage: run_pipeline <db_type>   (mysql, psql, mongodb, sqlite)
run_pipeline() {
    dbtype="$1"

    cd ../docker
    pwd
    stop_all_databases

    # SQLite is a local file, it has no container
    if [ "$dbtype" != "sqlite" ]; then
        ./compose-"$dbtype".sh
        sleep 30
    fi

    cd ../bin
    pwd
    ./run.sh file:///home/amos/develop/mavencentral/repo.maven.apache.org/maven2/.index/ "$dbtype"
}

target="${1:-ALL}"

case "$target" in
    ALL|all)
        for dbtype in $DB_TYPES; do
            run_pipeline "$dbtype"
        done
        ;;
    mysql|psql|mongodb|sqlite)
        run_pipeline "$target"
        ;;
    *)
        echo "Invalid database type: $target. Expected ALL, or one of: $DB_TYPES"
        exit 1
        ;;
esac
