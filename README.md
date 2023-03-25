# sz - find what's eating your disk space

## About

This is an improved version of `du`, written with Kotlin Native. It's intended to quickly answer the question that most of us
probably use `du` for, i.e. "what the hell is using all my disk space"?

It contains the following improvements:
* It's much faster
* It skips virtual filesystems like /proc and /sys
* It skips paths mounted on other devices from the starting path
* It sorts the output by file size, high to low
* It only returns results for directories using at least 1% of all files under the path  

## Running

./sz {dir} [threads] [sleepTime]

* dir - directory to scan
* threads - how many threads to execute when recursing the tree
* sleepTime - how many microseconds the main thread should pause when waiting for children to finish (this likely is a function of the underlying hardware)

Example:

./sz /tmp 50 100

It supplies sane defaults also:

./sz /home/you/Downloads 

## Status

It works, output matches `du`, and it is approximately twice as fast as `du`. There is still plenty of room for optimization.

Known issue: running twice with a high number of threads returns a nonsensical result the second time. 

Known issue: the binary size is ridiculously large. Maybe this is just how kotlin native is?

Known issue: the code is pretty messy, especially in Main.kt. Refactorings are nigh.

## Building

apt install gcc-multilib
./build.sh
