import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const version = readFileSync(resolve(root, 'VERSION'), 'utf-8').trim()

if (!/^v\d+\.\d+\.\d+(?:[-\w.]*)?$/.test(version)) {
  throw new Error(`VERSION must look like vMAJOR.MINOR.PATCH, got: ${version}`)
}

const npmVersion = version.replace(/^v/, '')
const backendVersion = `${version}-SNAPSHOT`

function updateJsonVersion(path, nextVersion) {
  const absolutePath = resolve(root, path)
  const json = JSON.parse(readFileSync(absolutePath, 'utf-8'))
  json.version = nextVersion
  if (json.packages?.['']) {
    json.packages[''].version = nextVersion
  }
  writeFileSync(absolutePath, `${JSON.stringify(json, null, 2)}\n`, 'utf-8')
}

function replaceInFile(path, replacements) {
  const absolutePath = resolve(root, path)
  let content = readFileSync(absolutePath, 'utf-8')
  for (const [pattern, replacement] of replacements) {
    content = content.replace(pattern, replacement)
  }
  writeFileSync(absolutePath, content, 'utf-8')
}

updateJsonVersion('frontend/package.json', npmVersion)
updateJsonVersion('frontend/package-lock.json', npmVersion)

replaceInFile('backend/pom.xml', [
  [
    /(<artifactId>media-marshal<\/artifactId>\s*\r?\n\s*<version>)[^<]+(<\/version>)/,
    `$1${backendVersion}$2`,
  ],
])

replaceInFile('parser/main.py', [
  [/APP_VERSION_FALLBACK = "[^"]+"/, `APP_VERSION_FALLBACK = "${npmVersion}"`],
])

console.log(`Synchronized project metadata to ${version}`)
