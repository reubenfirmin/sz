#!/bin/bash

echo BUILDING KOTLIN

if ./gradlew assemble; then
	cp build/bin/sz/releaseExecutable/sz.kexe ./sz_kn
else
	echo Kotlin build failed!
fi	

echo BUILDING RUST

if cargo build --release; then
	cp target/release/sz ./sz_rs
else
	echo Rust build failed!
fi

echo BUILDING GO

pushd src/golang
if go build; then
	echo Success!
	popd
	mv src/golang/sz ./sz_go
else
	echo Go build failed!
fi
