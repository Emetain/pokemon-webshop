/** Longest side a scanned photo is sent at: enough for a collector number, about a thousand tokens at the model. */
export const SCAN_MAX_SIDE = 1280
const SCAN_QUALITY = 0.85

/**
 * Shrinks a phone photo to a JPEG of at most [maxSide] pixels before it's uploaded (phase 11), so a scan is quick on a
 * phone network and cheap at the model. The photo turns upright the way the camera held it.
 */
export async function shrinkPhoto(file, maxSide = SCAN_MAX_SIDE) {
  const image = await decode(file)
  const scale = Math.min(1, maxSide / Math.max(image.width, image.height))
  const width = Math.round(image.width * scale)
  const height = Math.round(image.height * scale)
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  canvas.getContext('2d').drawImage(image, 0, 0, width, height)
  image.close?.()
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('photo'))), 'image/jpeg', SCAN_QUALITY),
  )
}

async function decode(file) {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' })
    } catch {
      // Some browsers can't decode every format this way; the image element below can.
    }
  }
  const url = URL.createObjectURL(file)
  try {
    const image = new Image()
    image.src = url
    await image.decode()
    return image
  } finally {
    URL.revokeObjectURL(url)
  }
}
