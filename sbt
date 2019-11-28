#!/usr/bin/env bash

./sbt-dist/bin/sbt -J-Xmx5048M -J-XX:MaxMetaspaceSize=2024m -jvm-debug 9999 "$@"