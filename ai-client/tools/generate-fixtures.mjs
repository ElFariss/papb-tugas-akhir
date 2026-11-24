// Regenerates src/test/resources/fixtures/*.csv from the ORIGINAL web implementation.
//
//   node tools/generate-fixtures.mjs <path-to-tunarasa> <path-to-fingerpose-0.1.0-package>
//
// Not part of the build: run it only when the sign definitions change upstream.
import { createRequire } from 'node:module'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const [tunarasa, fingerposePath] = process.argv.slice(2)
const fp = createRequire(import.meta.url)(join(fingerposePath, 'dist/fingerpose.js'))
const out = join(dirname(fileURLToPath(import.meta.url)), '../src/test/resources/fixtures')
const LETTERS = [...'ABCDEFGHIJKLMNOPQRSTUVWXYZ']

const signs = LETTERS.map((letter) => {
  const name = `${letter.toLowerCase()}Sign`
  const source = readFileSync(join(tunarasa, `frontend/src/components/handsigns/${letter}sign.ts`), 'utf8')
    .replace(/^import .*$/m, '')
    .replace('export const', 'const')
  return new Function('Finger', 'FingerCurl', 'FingerDirection', 'GestureDescription', `${source}; return ${name}`)(
    fp.Finger, fp.FingerCurl, fp.FingerDirection, fp.GestureDescription,
  )
})

// Seeded PRNG (mulberry32) so the output is reproducible.
let seed = 20261124
const random = () => {
  seed = (seed + 0x6d2b79f5) | 0
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}
const uniform = (a, b) => a + (b - a) * random()
const rad = (d) => (d * Math.PI) / 180
const round = (v) => Math.round(v * 100) / 100

const wrist = [320, 360, 0]
const SPREAD = [-35, -15, 0, 12, 25] // degrees clockwise from the hand axis, thumb..pinky, mirrored view
const BONES = [[40, 35, 30, 28], [95, 45, 28, 22], [92, 50, 32, 24], [88, 45, 30, 23], [85, 35, 22, 20]]

// A finger is a planar chain that flexes towards the camera.
const makeFinger = (f, axis, flex, sway) => {
  const angle = rad(axis + SPREAD[f] + sway)
  const direction = [Math.sin(angle), -Math.cos(angle)]
  let [x, y, z] = wrist
  let bend = 0
  return BONES[f].map((length, i) => {
    bend += i === 0 ? 0 : rad(flex[i - 1])
    x += direction[0] * Math.cos(bend) * length
    y += direction[1] * Math.cos(bend) * length
    z -= Math.sin(bend) * length
    return [round(x), round(y), round(z)]
  })
}
const randomFinger = (f, axis, sway = 40) => {
  const amount = random()
  const flex = [uniform(0, 95), uniform(0, 110), uniform(0, 80)].map((v) => v * amount)
  return makeFinger(f, axis, flex, uniform(-sway, sway))
}
const randomHand = () => {
  const axis = uniform(-180, 180)
  return [wrist, ...[0, 1, 2, 3, 4].flatMap((f) => randomFinger(f, axis))]
}
const noiseHand = () => Array.from({ length: 21 }, () => [round(uniform(0, 640)), round(uniform(0, 480)), round(uniform(-120, 120))])

const CURLS = ['No Curl', 'Half Curl', 'Full Curl']
const DIRECTIONS = ['Vertical Up', 'Vertical Down', 'Horizontal Left', 'Horizontal Right', 'Diagonal Up Right', 'Diagonal Up Left', 'Diagonal Down Right', 'Diagonal Down Left']
const estimate = (landmarks) => {
  const { poseData, gestures } = new fp.GestureEstimator(signs).estimate(landmarks, -100)
  return {
    curls: poseData.map((p) => CURLS.indexOf(p[1])),
    directions: poseData.map((p) => DIRECTIONS.indexOf(p[2])),
    scores: gestures.map((g) => g.score),
  }
}
// Same selection rule as handpose-service.ts
const webLetter = (scores) => {
  const max = Math.max(...scores)
  return max >= 5.0 ? LETTERS[scores.indexOf(max)] : '-'
}

// --- 1. Parity: random hands with every intermediate value.
const parity = []
for (let i = 0; i < 200; i++) {
  const landmarks = i % 4 === 3 ? noiseHand() : randomHand()
  const e = estimate(landmarks)
  parity.push([...landmarks.flat(), ...e.curls, ...e.directions, ...e.scores].join(','))
}
writeFileSync(join(out, 'fingerpose_parity.csv'), parity.join('\n') + '\n')

// --- 2. Synthetic alphabet: hands built to satisfy each sign's own definition.
const synthetic = []
for (const [s, sign] of signs.entries()) {
  for (let n = 0; n < 10; n++) {
    const axis = uniform(-60, 120)
    const landmarks = [wrist]
    for (const f of [0, 1, 2, 3, 4]) {
      const curl = sign.curls[f][0][0]
      const direction = sign.directions[f]?.at(-1)[0]
      let found = null
      for (let attempt = 0; attempt < 200000 && !found; attempt++) {
        const finger = randomFinger(f, axis, 180)
        const probe = Array.from({ length: 21 }, () => wrist)
        finger.forEach((p, i) => { probe[f * 4 + 1 + i] = p })
        const e = estimate(probe)
        if (e.curls[f] === curl && (direction === undefined || e.directions[f] === direction)) found = finger
      }
      if (!found) throw new Error(`No pose for ${LETTERS[s]} finger ${f}`)
      landmarks.push(...found)
    }
    const noisy = landmarks.map((p) => p.map((v) => round(v + uniform(-2, 2))))
    synthetic.push([LETTERS[s], webLetter(estimate(noisy).scores), ...noisy.flat()].join(','))
  }
}
writeFileSync(join(out, 'synthetic_alphabet.csv'), synthetic.join('\n') + '\n')

const agree = synthetic.filter((r) => r[0] === r[2]).length
console.log(`parity rows: ${parity.length}, synthetic rows: ${synthetic.length}, web app letter == intended: ${agree}`)
for (const l of LETTERS) console.log(l, synthetic.filter((r) => r[0] === l).map((r) => r[2]).join(''))
