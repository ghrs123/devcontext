import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname);
const DIST_DIR = path.join(ROOT, 'dist');
const DIST_FILE = path.join(DIST_DIR, 'fitvision-widget.min.js');
const MAX_GZIP_BYTES = 50 * 1024;

const { version } = JSON.parse(
  fs.readFileSync(path.join(ROOT, 'package.json'), 'utf8')
);
const VERSIONED_FILE = path.join(DIST_DIR, `fitvision-widget.${version}.min.js`);

function fail(message) {
  console.error(message);
  process.exit(1);
}

validateArtifact(DIST_FILE, 'dist/fitvision-widget.min.js');

const content = fs.readFileSync(DIST_FILE, 'utf8');
const gzipSize = gzipSync(content).length;
const sizeKb = (gzipSize / 1024).toFixed(2);

fs.copyFileSync(DIST_FILE, VERSIONED_FILE);
validateArtifact(VERSIONED_FILE, `fitvision-widget.${version}.min.js`);

console.log(`✓ Build OK — ${sizeKb}KB gzipped (latest + v${version})`);

function validateArtifact(filePath, label) {
  if (!fs.existsSync(filePath)) {
    fail(`✗ Build artifact missing — ${label}`);
  }

  const artifactContent = fs.readFileSync(filePath, 'utf8');
  const artifactTrimmed = artifactContent.trimStart();
  const artifactLooksLikeIife =
    artifactTrimmed.startsWith('(function(') ||
    artifactTrimmed.startsWith('!function(') ||
    artifactTrimmed.startsWith('(()=>') ||
    artifactTrimmed.startsWith('(()=>{') ||
    artifactTrimmed.startsWith('var FitVision=');

  if (!artifactLooksLikeIife) {
    fail(`✗ Build invalid — ${label} is not IIFE-like`);
  }

  const artifactGzip = gzipSync(artifactContent).length;
  const artifactKb = (artifactGzip / 1024).toFixed(2);
  if (artifactGzip > MAX_GZIP_BYTES) {
    fail(`✗ Build too large — ${label}: ${artifactKb}KB gzipped`);
  }
}
