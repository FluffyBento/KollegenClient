use std::{env, fs, io::Write, path::Path};
use wit_component::{ComponentEncoder, DecodedWasm};
use wit_parser::{WorldItem, WorldKey};
use zip::{write::SimpleFileOptions, CompressionMethod, ZipWriter};

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = env::args().collect();
    if args.len() < 4 {
        return Err("usage: hmod-packer <core.wasm> <horizon-mod.toml> <out.hmod>".into());
    }
    let core_path = Path::new(&args[1]);
    let manifest_path = Path::new(&args[2]);
    let out_path = Path::new(&args[3]);

    let core = fs::read(core_path)?;
    let manifest = fs::read_to_string(manifest_path)?;

    let component = ComponentEncoder::default()
        .module(&core)?
        .validate(true)
        .encode()?;

    if let DecodedWasm::Component(resolve, world) = wit_component::decode(&component)? {
        for (key, item) in &resolve.worlds[world].imports {
            let name = match (key, item) {
                (WorldKey::Interface(id), _) | (_, WorldItem::Interface { id, .. }) => {
                    resolve.id_of(*id).unwrap_or_else(|| resolve.name_world_key(key))
                }
                _ => resolve.name_world_key(key),
            };
            println!("component import: {}", name);
        }
    }

    if let Some(parent) = out_path.parent() {
        fs::create_dir_all(parent)?;
    }
    let file = fs::File::create(out_path)?;
    let mut writer = ZipWriter::new(file);
    let opts = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    writer.start_file("horizon-mod.toml", opts)?;
    writer.write_all(manifest.as_bytes())?;
    writer.start_file("mod.wasm", opts)?;
    writer.write_all(&component)?;
    writer.finish()?;
    println!("packed {}", out_path.display());
    Ok(())
}
