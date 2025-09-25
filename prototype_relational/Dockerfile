FROM ubuntu:22.04

COPY ./build_db.py build_db.py
RUN apt-get update && apt-get install -y openjdk-19-jdk libpq-dev python-is-python3 python3-pip pip screen curl
RUN pip install psycopg2
EXPOSE 8080
