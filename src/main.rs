use std::error::Error;
use std::{fmt, fs};
use std::collections::HashMap;
use std::sync::mpsc;
use threadpool::ThreadPool;
use std::path::PathBuf;

/**
 * NOTE - this rust version is extremely rudimentary. I'm starting to learn rust by building the
 * equivalent of the working (and more polished) kotlin native implementation.
 */
fn main() {
    let args: Vec<String> = std::env::args().collect();

    if args.len() < 2 {
        println!("Usage: {} <directory>", args[0]);
        return;
    }

    let dir = &args[1];
    let result = scan_path(dir, 10);
    let dir_size = result.get(dir);
    println!("Total size: {} bytes", dir_size.unwrap());
}

fn scan_path(dir: &String, threads: usize) -> HashMap<String, u64> {
    let root_path = PathBuf::from(dir);

    let (tx, rx) = std::sync::mpsc::channel();

    let pool = ThreadPool::new(threads);
    let ptx = tx.clone();

    submit(root_path, pool, ptx);

    let mut results = HashMap::new();

    let result = rx.recv().unwrap();
    results.insert(dir.to_string(),result.size);
    results
}

fn submit(path: PathBuf, pool: ThreadPool, tx: mpsc::Sender<DirMetadata>) {
    pool.execute (move || {
        let metadata = process_directory(&path).unwrap();
        tx.send(metadata).unwrap();
    });
}

fn process_directory(dir_path: &PathBuf) -> Result<DirMetadata, Box<dyn Error>> {
    let metadata = fs::metadata(dir_path)?;
    if !metadata.is_dir() {
        return Result::Err(Box::new(MyError("Not a dir".into())))
    }

    let mut result = DirMetadata {
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
    paths: Vec<PathBuf>,
    size: u64
}