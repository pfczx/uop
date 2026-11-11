#!/usr/bin/env bash
set -e

./mvnw -pl uop-backend \
  compile \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.platform.uop.demo.UopDemo \
  -Dexec.classpathScope=runtime
