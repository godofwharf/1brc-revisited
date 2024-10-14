#!/bin/sh

JAVA_OPTS="--enable-preview --add-modules jdk.incubator.vector -DpageSize=262144 -XX:+UseParallelGC -Xms2600m -XX:ParallelGCThreads=8 -XX:Tier4CompileThreshold=1000 -XX:Tier3CompileThreshold=500 -XX:Tier3CompileThreshold=250 -Dthreads=9 -Djava.util.concurrent.ForkJoinPool.common.parallelism=9"
java $JAVA_OPTS --class-path onebrc-core/target/onebrc-core-1.0.0-SNAPSHOT.jar dev.godofwharf.onebrc.CalculateAverage"$1" 2>/dev/null