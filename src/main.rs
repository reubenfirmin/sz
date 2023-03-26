use std::error::Error;
use std::{fmt, fs};
use std::path::PathBuf;

fn main() {
    let args: Vec<String> = std::env::args().collect();

    if args.len() < 2 {
        println!("Usage: {} <directory>", args[0]);
        return;
    }

    let root_dir = PathBuf::from(&args[1]);

    let metadata = process_directory(&root_dir).expect("Failed");


    println!("Total size: {} bytes", metadata.size);
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