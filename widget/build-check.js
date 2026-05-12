import fs from 'node:fs';
import path from 'node:path';
import { gzipSync } from 'node:zlib';

const DIST_FILE = path.resolve('dist/fitvision-widget.min.js');
const MAX_GZIP_BYTES = 50 * 1024;

function fail(message) {
  console.error(message);
  process.exit(1);
}

if (!fs.existsSync(DIST_FILE)) {
  fail('✗ Build artifact missing — dist/fitvision-widget.min.js');
}

const content = fs.readFileSync(DIST_FILE, 'utf8');
const trimmed = content.trimStart();
const looksLikeIife =
  trimmed.startsWith('(function(') ||
  trimmed.startsWith('!function(') ||
  trimmed.startsWith('(()=>') ||
  trimmed.startsWith('(()=>{') ||
  trimmed.startsWith('var FitVision=');

if (!looksLikeIife) {
  fail('✗ Build invalid — output is not IIFE-like');
}

const gzipSize = gzipSync(content).length;
const sizeKb = (gzipSize / 1024).toFixed(2);

if (gzipSize > MAX_GZIP_BYTES) {
  fail(`✗ Build too large — ${sizeKb}KB gzipped`);
}

console.log(`✓ Build OK — ${sizeKb}KB gzipped`);
