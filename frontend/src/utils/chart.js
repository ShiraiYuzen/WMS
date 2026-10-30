/**
 * ECharts 公共样式。
 *
 * 看板（dashboard）和效率复盘（analytics）画的是同一批趋势图，
 * 之前两处各写了一份坐标轴/图例配置——修「轴名压住图例」那个 bug 时就得改两遍。
 * 统一放这里，改样式只改一处。
 *
 * 只收敛「样式」，不收敛「数据」：series 的字段、颜色语义仍在各自页面里写。
 */

/** 坐标轴基础样式（轴线颜色、隐藏刻度、标签字号） */
export const axisStyle = {
  axisLine: { lineStyle: { color: '#e2e8f0' } },
  axisTick: { show: false },
  axisLabel: { color: '#94a3b8', fontSize: 11 }
}

/** 网格分割线（浅色虚线） */
export const splitLine = { lineStyle: { color: '#f1f5f9', type: 'dashed' } }

/** 图例：右上角横排 */
export const legend = {
  right: 0,
  top: 0,
  icon: 'roundRect',
  itemWidth: 9,
  itemHeight: 9,
  textStyle: { color: '#64748b', fontSize: 11 }
}

/** 图例：底部横排（饼图用） */
export const legendBottom = {
  bottom: 0,
  icon: 'roundRect',
  itemWidth: 9,
  itemHeight: 9,
  textStyle: { color: '#64748b', fontSize: 11 }
}

/** 趋势图绘图区：top 默认 38，留给顶部图例 */
export function trendGrid(top = 38) {
  return { left: 8, right: 8, top, bottom: 4, containLabel: true }
}

/**
 * 趋势图 X 轴。
 * 后端在「单日」区间按小时返回（label 形如 `2026-10-20 09:00`），多日按天（`2026-10-20`），
 * 这里统一裁成 `09:00` / `10-20`。
 */
export function trendXAxis(labels) {
  return {
    type: 'category',
    data: (labels || []).map(xLabel),
    ...axisStyle
  }
}

/** 把后端的 label 裁成 X 轴要显示的形式（内部用） */
function xLabel(label) {
  if (!label) return ''
  return label.length > 10 ? label.slice(11, 16) : String(label).slice(5)
}
