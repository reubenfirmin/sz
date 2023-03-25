# sz - find what's eating your disk space

## About

An experiment with parallelization using Kotlin native. This is similar to `du`, except that it produces output more 
specifically targeted to the question "what the hell is using up my disk space?"

Given a path, and optionally number of threads, it will return all sub-paths that consume more than 1% of the space
within the directory hierarchy under the path. It does not list individual files, but does provide the total size of 
files directly within the specific path. Output is sorted, and really pretty!

## Running

./sz [dir] [threads] [sleepTime]

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

TODO filter for the same device

## Building

apt install gcc-multilib
./build.sh
