#!/bin/bash

mvn install -Dmaven.test.skip -Dchangelist=-seno-`git rev-parse --short HEAD`
