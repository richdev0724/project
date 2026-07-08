const express = require('express')
const puppeteer = require('puppeteer')
const router = express.Router()

/**
 * POST /api/screenshot/modal
 * Generates a high-definition screenshot of a modal component
 */
router.post('/modal', async (req, res) => {
  try {
    const { html, css, width = 400, height = 400, type = 'png' } = req.body

    if (!html) {
      return res.status(400).json({ error: 'HTML content is required' })
    }

    // Launch Puppeteer
    const browser = await puppeteer.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    })

    const page = await browser.newPage()

    // Set viewport for high-definition screenshots
    await page.setViewport({
      width: width * 2, // Double resolution for HD
      height: height * 2,
      deviceScaleFactor: 2
    })

    // Set the HTML content
    const fullHtml = `
      <!DOCTYPE html>
      <html>
        <head>
          <meta charset="utf-8">
          <style>
            body {
              margin: 0;
              padding: 20px;
              background: transparent;
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            }
            ${css || ''}
          </style>
        </head>
        <body>
          ${html}
        </body>
      </html>
    `

    await page.setContent(fullHtml, { waitUntil: 'networkidle0' })

    // Wait a bit for any animations or dynamic content
    await page.waitForTimeout(500)

    // Take screenshot
    const screenshot = await page.screenshot({
      type: type === 'jpeg' ? 'jpeg' : 'png',
      quality: type === 'jpeg' ? 95 : undefined,
      omitBackground: true // Transparent background
    })

    await browser.close()

    // Set appropriate headers
    res.setHeader('Content-Type', type === 'jpeg' ? 'image/jpeg' : 'image/png')
    res.setHeader('Content-Length', screenshot.length)
    
    if (type === 'copy') {
      // For copy functionality, return base64
      const base64 = screenshot.toString('base64')
      res.json({ 
        success: true, 
        data: `data:image/${type === 'jpeg' ? 'jpeg' : 'png'};base64,${base64}`,
        type: type === 'jpeg' ? 'jpeg' : 'png'
      })
    } else {
      // For save functionality, return the image directly
      res.send(screenshot)
    }

  } catch (error) {
    console.error('Screenshot generation failed:', error)
    res.status(500).json({ 
      error: 'Failed to generate screenshot',
      details: error.message 
    })
  }
})

module.exports = router
