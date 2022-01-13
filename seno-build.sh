#!/bin/bash

VERSION="-seno-"`git rev-parse --short HEAD`
mvn clean install -Dchangelist=$VERSION
mvn clean package -Dchangelist=$VERSION -Dportable=true -P compressXZ -f weasis-distributions
