use std::error::Error;
use std::{fmt, fs};
use std::collections::HashMap;
use threadpool::ThreadPool;
use std::path::PathBuf;
use std::thread::yield_now;
use std::sync::mpsc::Sender;

/**
 * NOTE - this version is rudimentary / hacky. I'm starting to learn rust by building the
 * equivalent of the working (and more polished) kotlin native implementation.
 */
fn main() {
    let args: Vec<String> = std::env::args().collect();

    if args.len() < 2 {
        println!("Usage: {} <directory>", args[0]);
        return;
    }

    let dir = &args[1];
    let all_results = scan_path(dir, 10);
    let mut result: Vec<_> = all_results.iter().collect();
    result.sort_by(|a, b| b.1.cmp(a.1));

    for x in result.iter().take(10) {
        println!("{}\t\t{}", x.1, x.0)
    }
}

fn scan_path(dir: &String, threads: usize) -> HashMap<String, u64> {

    let (tx, rx) = std::sync::mpsc::channel();

    let pool = ThreadPool::new(threads);

    let mut jobs = 0;
    let mut found = 0;
    let mut results = HashMap::new();

    jobs += 1;
    submit(PathBuf::from(dir), &pool, tx.clone());

    while jobs > found  {
        // pick results off the receiving channel
        let mut iter = rx.try_iter();
        while let Some(result) = iter.next() {
            match result {
                Ok(it) => {
                    let displayed = it.path.display().to_string();
                    results.insert(displayed,it.size);
                    for subpath in it.paths {
                        jobs += 1;
                        submit(subpath, &pool, tx.clone());
                    }
                },
                Err(msg) => { println!("{}", msg.to_string()) }
            }
            found += 1;
        }
        yield_now()
    }
    results
}

fn submit(path: PathBuf, pool: &ThreadPool, tx: Sender<Result<DirMetadata, Box<dyn Error + Send + Sync>>>) {
    pool.execute (move || {
        let result = process_directory(&path);
        tx.send(result).expect("Couldn't send!");
    });
}

fn process_directory(dir_path: &PathBuf) -> Result<DirMetadata, Box<dyn Error + Send + Sync>> {
    let metadata = fs::metadata(dir_path)?;
    if !metadata.is_dir() {
        return Result::Err(Box::new(MyError("Not a dir".into())))
    }

    let mut result = DirMetadata {
        path: dir_path.clone(),
        size: 0,
        paths: Vec::new()
    };

    let mut size = 0;

    for entry in fs::read_dir(dir_path)? {
        let entry = entry?;
        let path = entry.path();
        if path.is_dir() {
            result.paths.push(path);
        } else {
            size += match entry.metadata() {
                Ok(size) => size.len(),
                Err(_) => 0
            }
        }
    }

    result.size = size;
    Ok(result)
}

#[derive(Debug)]
struct MyError(String);

impl fmt::Display for MyError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "There is an error: {}", self.0)
    }
}

impl Error for MyError{}

struct DirMetadata {
    path: PathBuf,
    paths: Vec<PathBuf>,
    size: u64
}