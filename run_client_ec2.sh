#!/bin/bash
export MYIP=`curl http://169.254.169.254/latest/meta-data/local-ipv4`
./sbt -Dgeotrellis.cluster_seed="$MYIP" -Dgeotrellis.port=8080  -Dgeotrellis.hostname="$MYIP" "run-main geotrellis.demo.RemoteClient"

