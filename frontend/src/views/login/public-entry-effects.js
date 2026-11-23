function drawWarehousePlan(context, originX, originY, scale, racks, route) {
  const project = ([x, y, z = 0]) => [
    originX + (x - y) * .87 * scale,
    originY + ((x + y) * .44 - z) * scale
  ]

  function drawPath(points, stroke, fill) {
    const [first, ...rest] = points
    context.beginPath()
    context.moveTo(...project(first))
    for (const point of rest) context.lineTo(...project(point))
    if (fill) {
      context.closePath()
      context.fillStyle = fill
      context.fill()
    }
    context.strokeStyle = stroke
    context.stroke()
  }

  function drawBox(x, y, z, length, depth, height, face, top) {
    const right = x + length
    const front = y + depth
    const upper = z + height
    const edge = '#91e2ec79'
    drawPath([[x, front, z], [right, front, z], [right, front, upper], [x, front, upper]], edge, face)
    drawPath([[right, front, z], [right, y, z], [right, y, upper], [right, front, upper]], edge, '#12384cba')
    drawPath([[x, y, upper], [right, y, upper], [right, front, upper], [x, front, upper]], edge, top)
  }

  function drawRack([x, y, length, depth, height, offset]) {
    const bayCount = Math.round(length / 31)
    const bayWidth = length / bayCount
    const right = x + length
    const front = y + depth
    const frame = '#a3e6ee99'

    drawPath([[x, y, 0], [x, y, height], [right, y, height], [right, y, 0]], frame)
    for (let level = 0; level < 3; level++) {
      const shelf = 4 + level * height * .32
      drawBox(x, y, shelf, length, depth, 2, '#1d597075', '#2b688081')
      for (let bay = 0; bay < bayCount; bay++) {
        if ((bay + 2 * level + offset) % 5 === 1) continue
        const accent = (bay * 2 + level + offset) % 5 === 0
        drawBox(x + bay * bayWidth + 4, y + 3, shelf + 2, bayWidth - 8, depth - 7,
          9 + (bay + level + offset) % 3 * 3,
          accent ? '#2d988fbd' : '#2c627ebd', accent ? '#6acfc7b0' : '#4b879e9e')
      }
    }

    for (let bay = 0; bay <= bayCount; bay++) {
      const post = x + bay * bayWidth
      drawPath([[post, front, 0], [post, front, height]], frame)
    }
    drawPath([[x, y, height], [x, front, height], [right, front, height], [right, y, height]], frame)
  }

  context.lineWidth = Math.max(.7, .9 * scale)
  context.lineJoin = 'round'
  for (let line = -128; line <= 128; line += 32) {
    drawPath([[-160, line], [160, line]], '#7ec9da27')
    drawPath([[line, -128], [line, 128]], '#7ec9da27')
  }
  drawPath([[-160, -128], [160, -128], [160, 128], [-160, 128], [-160, -128]], '#8cd1dd48')

  context.setLineDash([5 * scale, 5 * scale])
  context.lineWidth = Math.max(1, 1.35 * scale)
  drawPath(route, '#81eee4bd')
  context.setLineDash([])
  context.strokeStyle = '#81eee4dc'
  context.fillStyle = '#79e9df38'
  for (const point of [route[0], route[route.length - 1]]) {
    const [x, y] = project(point)
    context.beginPath()
    context.arc(x, y, 5 * scale, 0, Math.PI * 2)
    context.fill()
    context.stroke()
  }

  context.lineWidth = Math.max(.65, .85 * scale)
  racks.forEach(drawRack)
}

export function mountWarehouseBackground(canvas) {
  const context = canvas.getContext('2d')
  if (!context) return

  const leftRacks = [[-110, -103, 202, 24, 59, 2], [-139, -35, 229, 24, 71, 1], [-101, 40, 189, 25, 77, 4]]
  const rightRacks = [[-92, -86, 211, 25, 65, 0], [-118, -14, 198, 24, 70, 3], [-95, 56, 182, 24, 54, 2]]
  const leftRoute = [[-148, 103], [117, 103], [117, -58], [-118, -58]]
  const rightRoute = [[144, -103], [144, 36], [-135, 36], [-135, 105], [71, 105]]
  const section = canvas.parentElement

  function paint() {
    const { clientWidth: width, clientHeight: height } = section
    if (!width || !height) return

    const ratio = Math.min(devicePixelRatio || 1, 2)
    canvas.width = Math.round(width * ratio)
    canvas.height = Math.round(height * ratio)
    context.setTransform(ratio, 0, 0, ratio, 0, 0)

    const compact = width < 760
    const scale = Math.min(1.35, height / 600, compact ? width / 570 : width / 1170)
    drawWarehousePlan(context, width * .16, height * (compact ? .23 : .55), scale, leftRacks, leftRoute)
    drawWarehousePlan(context, width * .84, height * (compact ? .88 : .61), scale, rightRacks, rightRoute)
  }

  paint()
  const observer = new ResizeObserver(paint)
  observer.observe(section)
  return () => observer.disconnect()
}

export function mountLoginReveal(section, root) {
  if (typeof IntersectionObserver !== 'function' || matchMedia('(prefers-reduced-motion: reduce)').matches) return

  const card = section.querySelector('.card')
  const observer = new IntersectionObserver(entries => {
    if (section.dataset.reveal !== 'waiting' || !entries.some(entry => entry.isIntersecting)) return

    section.dataset.reveal = 'shown'
    observer.disconnect()
  }, { root, rootMargin: '0px 0px -64px 0px' })

  section.dataset.reveal = 'waiting'
  observer.observe(card)
  function revealOnFocus() {
    delete section.dataset.reveal
    observer.disconnect()
  }
  card.addEventListener('focusin', revealOnFocus, { once: true })

  return () => {
    observer.disconnect()
    card.removeEventListener('focusin', revealOnFocus)
  }
}

export function mountScreenScrolling(root, login) {
  let handledDirection = 0
  let lastWheelTime = -Infinity
  const gestureGap = 180

  function onWheel(event) {
    if (event.ctrlKey || !event.deltaY || Math.abs(event.deltaX) > Math.abs(event.deltaY)) return

    const direction = Math.sign(event.deltaY)
    const now = performance.now()
    if (direction === handledDirection && now - lastWheelTime < gestureGap) {
      lastWheelTime = now
      event.preventDefault()
      return
    }
    handledDirection = 0

    const loginTop = login.getBoundingClientRect().top - root.getBoundingClientRect().top
    const goingToLogin = direction > 0 && loginTop > 2
    const goingToHero = direction < 0 && root.scrollTop > 2 && loginTop >= -2
    if (!goingToLogin && !goingToHero) return

    event.preventDefault()
    handledDirection = direction
    lastWheelTime = now
    root.scrollTo({ top: goingToLogin ? root.scrollTop + loginTop : 0 })
  }

  root.addEventListener('wheel', onWheel, { passive: false })
  return () => root.removeEventListener('wheel', onWheel)
}

export function mountParticleTitle(hero, root) {
  const stage = hero.querySelector('.particle-stage')
  const canvas = hero.querySelector('canvas')
  const context = canvas.getContext('2d')
  const mask = document.createElement('canvas')
  const pen = mask.getContext('2d', { willReadFrequently: true })
  if (!context || !pen) return

  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)')
  const colors = ['#74c8e1', '#91e9ef', '#b3fff2', '#defff9']
  let cursor = null
  let particles = colors.map(() => [])
  let dust = []
  let width = 0
  let height = 0
  let spreadRadius = 0
  let visible = true
  let frame = 0
  let lastTime = 0

  function paint() {
    context.clearRect(0, 0, width, height)
    context.fillStyle = '#73dce980'
    dust.forEach(point => context.fillRect(point.x, point.y, point.size, point.size))

    colors.forEach((color, index) => {
      context.fillStyle = color
      context.beginPath()
      for (const particle of particles[index]) {
        const { x, y, size } = particle
        context.rect(x - size / 2, y - size / 2, size, size)
      }
      context.fill()
    })
  }

  function rebuild() {
    const bounds = stage.getBoundingClientRect()
    const heroBounds = hero.getBoundingClientRect()
    const stageWidth = Math.round(bounds.width)
    const stageHeight = Math.round(bounds.height)
    const left = bounds.left - heroBounds.left
    const top = bounds.top - heroBounds.top
    width = hero.clientWidth
    height = hero.clientHeight
    if (!width || !height || !stageWidth || !stageHeight) return

    const ratio = Math.min(devicePixelRatio || 1, 2)
    canvas.width = Math.round(width * ratio)
    canvas.height = Math.round(height * ratio)
    context.setTransform(ratio, 0, 0, ratio, 0, 0)
    mask.width = stageWidth
    mask.height = stageHeight

    const fontFamily = '"Arial Black", Arial, sans-serif'
    pen.font = `900 100px ${fontFamily}`
    const sample = pen.measureText('WMS')
    const sampleHeight = sample.actualBoundingBoxAscent + sample.actualBoundingBoxDescent
    const size = Math.floor(100 * Math.min(stageWidth * .79 / sample.width, stageHeight * .68 / sampleHeight))
    pen.font = `900 ${size}px ${fontFamily}`
    const text = pen.measureText('WMS')
    spreadRadius = (text.actualBoundingBoxAscent + text.actualBoundingBoxDescent) / 2
    pen.fillText('WMS', (stageWidth - text.width) / 2,
      (stageHeight + text.actualBoundingBoxAscent - text.actualBoundingBoxDescent) / 2)

    const pixels = pen.getImageData(0, 0, stageWidth, stageHeight).data
    const step = stageWidth < 550 ? 3 : 4
    particles = colors.map(() => [])
    for (let y = 0; y < stageHeight; y += step) {
      for (let x = 0; x < stageWidth; x += step) {
        if (pixels[(y * stageWidth + x) * 4 + 3] < 146) continue

        const noise = ((x * 17 + y * 31) % 97) / 97
        const shade = Math.min(3, Math.floor((1 - y / stageHeight) * 2.7 + noise * 1.6))
        const dot = step * (.45 + noise * .22)
        const homeX = left + x
        const homeY = top + y
        particles[shade].push({ homeX, homeY, x: homeX, y: homeY, size: dot })
      }
    }

    dust = Array.from({ length: stageWidth < 550 ? 24 : 55 }, (_, index) => ({
      x: left + stageWidth * (.04 + ((index * 137.51) % 100) * .0092),
      y: top + stageHeight * (.07 + ((index * 61.8 + 17) % 100) * .0086),
      size: .6 + (index % 4) * .28
    }))
    paint()
    stage.classList.add('is-drawn')
    if (cursor) resume()
  }

  function tick(time) {
    frame = 0
    if (!visible || document.hidden || reducedMotion.matches) return

    const elapsed = lastTime ? Math.min((time - lastTime) / 16.67, 1.8) : 1
    const easing = 1 - .76 ** elapsed
    const radiusSquared = spreadRadius * spreadRadius
    let moving = false
    lastTime = time

    for (const group of particles) {
      for (const particle of group) {
        let targetX = particle.homeX
        let targetY = particle.homeY

        if (cursor) {
          const dx = particle.homeX - cursor.x
          const dy = particle.homeY - cursor.y
          const squaredDistance = dx * dx + dy * dy

          if (squaredDistance < radiusSquared) {
            const distance = Math.sqrt(squaredDistance)
            // Use the original direction so particles hold their places along the edge.
            targetX = cursor.x + (distance ? dx / distance : 1) * spreadRadius
            targetY = cursor.y + (distance ? dy / distance : 0) * spreadRadius
          }
        }

        const deltaX = targetX - particle.x
        const deltaY = targetY - particle.y
        if (Math.abs(deltaX) + Math.abs(deltaY) < .15) {
          particle.x = targetX
          particle.y = targetY
        } else {
          particle.x += deltaX * easing
          particle.y += deltaY * easing
          moving = true
        }
      }
    }

    paint()
    if (moving) frame = requestAnimationFrame(tick)
    else lastTime = 0
  }

  function resume() {
    if (!frame && visible && !document.hidden && !reducedMotion.matches) {
      frame = requestAnimationFrame(tick)
    }
  }

  function pause() {
    cancelAnimationFrame(frame)
    frame = lastTime = 0
    cursor = null
  }

  function onPointerMove(event) {
    if (event.pointerType === 'touch' || reducedMotion.matches) return
    const bounds = hero.getBoundingClientRect()
    cursor = { x: event.clientX - bounds.left, y: event.clientY - bounds.top }
    resume()
  }

  function onPointerLeave() {
    cursor = null
    resume()
  }

  function onVisibilityChange() {
    if (document.hidden) pause()
    else resume()
  }

  function onMotionChange() {
    pause()
    rebuild()
  }

  rebuild()
  const resize = new ResizeObserver(rebuild)
  resize.observe(stage)
  resize.observe(hero)
  const visibility = new IntersectionObserver(([entry]) => {
    visible = entry.isIntersecting
    if (visible) resume()
    else pause()
  }, { root })
  visibility.observe(stage)
  stage.addEventListener('pointermove', onPointerMove)
  stage.addEventListener('pointerleave', onPointerLeave)
  document.addEventListener('visibilitychange', onVisibilityChange)
  reducedMotion.addEventListener('change', onMotionChange)

  return () => {
    pause()
    resize.disconnect()
    visibility.disconnect()
    stage.removeEventListener('pointermove', onPointerMove)
    stage.removeEventListener('pointerleave', onPointerLeave)
    document.removeEventListener('visibilitychange', onVisibilityChange)
    reducedMotion.removeEventListener('change', onMotionChange)
  }
}
