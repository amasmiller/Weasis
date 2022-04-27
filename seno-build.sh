#!/bin/bash

# for debugging with IntelliJ
# mvn install -Dmaven.test.skip -Dchangelist=-seno-`git rev-parse --short HEAD`

# for packaging
mvn -P compressXZ -Dportable=true -Dchangelist=-seno-`git rev-parse --short HEAD` -f weasis-distributions clean package
