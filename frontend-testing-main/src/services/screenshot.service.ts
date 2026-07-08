/**
 * Screenshot Service
 * Handles modal screenshot generation and export functionality
 */

export interface ScreenshotOptions {
  width?: number
  height?: number
  type?: 'png' | 'jpeg'
  quality?: number
}

export interface ScreenshotResponse {
  success: boolean
  data?: string
  type?: string
  error?: string
}

/**
 * Generates a screenshot of a modal element
 */
export async function generateModalScreenshot(
  element: HTMLElement,
  options: ScreenshotOptions = {}
): Promise<ScreenshotResponse> {
  try {
    // Get the element's HTML and computed styles
    const html = element.outerHTML
    const computedStyles = getComputedStyles(element)
    
    // Prepare the request payload
    const payload = {
      html,
      css: computedStyles,
      width: options.width || 400,
      height: options.height || 400,
      type: options.type || 'png',
      quality: options.quality || 95
    }

    // Send request to backend
    const response = await fetch('/api/screenshot/modal', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload)
    })

    if (!response.ok) {
      const errorData = await response.json()
      throw new Error(errorData.error || 'Screenshot generation failed')
    }

    const result = await response.json()
    return result

  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error occurred'
    }
  }
}

/**
 * Gets computed styles for an element and its children
 */
function getComputedStyles(element: HTMLElement): string {
  const styles: string[] = []
  
  // Get styles for the element itself
  const elementStyles = window.getComputedStyle(element)
  const elementSelector = getElementSelector(element)
  
  if (elementSelector) {
    styles.push(`${elementSelector} {`)
    for (let i = 0; i < elementStyles.length; i++) {
      const property = elementStyles[i]
      const value = elementStyles.getPropertyValue(property)
      if (value && !isDefaultValue(property, value)) {
        styles.push(`  ${property}: ${value};`)
      }
    }
    styles.push('}')
  }

  // Get styles for child elements
  const children = element.querySelectorAll('*')
  children.forEach((child) => {
    const childStyles = window.getComputedStyle(child)
    const childSelector = getElementSelector(child as HTMLElement)
    
    if (childSelector) {
      styles.push(`${childSelector} {`)
      for (let i = 0; i < childStyles.length; i++) {
        const property = childStyles[i]
        const value = childStyles.getPropertyValue(property)
        if (value && !isDefaultValue(property, value)) {
          styles.push(`  ${property}: ${value};`)
        }
      }
      styles.push('}')
    }
  })

  return styles.join('\n')
}

/**
 * Generates a CSS selector for an element
 */
function getElementSelector(element: HTMLElement): string | null {
  if (element.id) {
    return `#${element.id}`
  }
  
  if (element.className) {
    const classes = element.className.split(' ').filter(c => c.trim())
    if (classes.length > 0) {
      return `.${classes.join('.')}`
    }
  }
  
  return element.tagName.toLowerCase()
}

/**
 * Checks if a CSS value is a default value
 */
function isDefaultValue(property: string, value: string): boolean {
  const defaults: Record<string, string[]> = {
    'margin': ['0px', '0'],
    'padding': ['0px', '0'],
    'border': ['none', '0px'],
    'background': ['rgba(0, 0, 0, 0)', 'transparent'],
    'color': ['rgb(0, 0, 0)', '#000000'],
    'font-size': ['16px'],
    'font-family': ['-apple-system', 'BlinkMacSystemFont'],
    'line-height': ['normal'],
    'text-align': ['start'],
    'display': ['block'],
    'position': ['static'],
    'visibility': ['visible'],
    'opacity': ['1'],
    'z-index': ['auto']
  }
  
  return defaults[property]?.includes(value) || false
}

/**
 * Copies an image to clipboard
 */
export async function copyImageToClipboard(imageDataUrl: string): Promise<boolean> {
  try {
    // Convert data URL to blob
    const response = await fetch(imageDataUrl)
    const blob = await response.blob()
    
    // Copy to clipboard
    await navigator.clipboard.write([
      new ClipboardItem({
        [blob.type]: blob
      })
    ])
    
    return true
  } catch (error) {
    return false
  }
}

/**
 * Downloads an image
 */
export function downloadImage(imageDataUrl: string, filename: string = 'modal-screenshot'): void {
  try {
    const link = document.createElement('a')
    link.href = imageDataUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  } catch (error) {
    // Silent fail for download
  }
}
