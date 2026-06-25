import { readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptsDir = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.resolve(scriptsDir, '..')
const versionText = (await readFile(path.join(rootDir, 'VERSION'), 'utf8')).trim()

if (!/^v\d+\.\d+\.\d+$/.test(versionText)) {
  throw new Error(`Invalid VERSION value: ${versionText}`)
}

const packageVersion = versionText.slice(1)

async function updateText(relativePath, update) {
  const filePath = path.join(rootDir, relativePath)
  const current = await readFile(filePath, 'utf8')
  const next = update(current)
  if (next === current) return
  await writeFile(filePath, next, 'utf8')
  console.log(`Updated ${relativePath} -> ${versionText}`)
}

async function updateJson(relativePath, update) {
  const filePath = path.join(rootDir, relativePath)
  const json = JSON.parse(await readFile(filePath, 'utf8'))
  update(json)
  await writeFile(filePath, `${JSON.stringify(json, null, 2)}\n`, 'utf8')
  console.log(`Updated ${relativePath} -> ${versionText}`)
}

await updateText('backend/pom.xml', current => current.replace(
  /(<artifactId>media-marshal<\/artifactId>\s*<version>)[^<]+(<\/version>)/,
  `$1${versionText}-SNAPSHOT$2`,
))

await updateJson('frontend/package.json', json => {
  json.version = packageVersion
})

await updateJson('frontend/package-lock.json', json => {
  json.version = packageVersion
  if (json.packages?.['']) {
    json.packages[''].version = packageVersion
  }
})

await updateText('parser/main.py', current => current.replace(
  /APP_VERSION_FALLBACK = "[^"]+"/,
  `APP_VERSION_FALLBACK = "${packageVersion}"`,
))
