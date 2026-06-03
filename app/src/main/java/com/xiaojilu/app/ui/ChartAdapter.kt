package com.xiaojilu.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PaintDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xiaojilu.app.R

class ChartAdapter(
    private val onMonthClick: (String) -> Unit
) : RecyclerView.Adapter<ChartAdapter.ChartViewHolder>() {

    private var data: List<AnalysisFragment.ChartItem> = emptyList()
    private var maxCount: Int = 1
    private var shouldAnimate = false
    private var selectedPosition: Int = -1

    private val barColors = listOf(
        "#3B82F6", "#6366F1", "#8B5CF6", "#A855F7",
        "#D946EF", "#EC4899", "#F43F5E", "#EF4444",
        "#F97316", "#F59E0B", "#EAB308", "#84CC16"
    )

    inner class ChartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val itemRoot: View = itemView.findViewById(R.id.chart_item_root)
        private val monthText: TextView = itemView.findViewById(R.id.chart_month)
        private val barContainer: LinearLayout = itemView.findViewById(R.id.chart_bar_container)
        private val barFill: View = itemView.findViewById(R.id.chart_bar_fill)
        private val countText: TextView = itemView.findViewById(R.id.chart_count)

        fun bind(item: AnalysisFragment.ChartItem, position: Int) {
            monthText.text = item.month.substring(5) + "月"
            countText.text = item.count.toString()

            val ratio = if (maxCount > 0) item.count.toFloat() / maxCount else 0f

            val colorIndex = position % barColors.size
            val barColor = Color.parseColor(barColors[colorIndex])
            val barColorEnd = lightenColor(barColor, 0.3f)

            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(barColor, barColorEnd)
            ).apply {
                cornerRadius = 6f
            }

            barFill.background = gradientDrawable

            if (shouldAnimate && item.count > 0) {
                val lp = barFill.layoutParams
                lp.width = 0
                barFill.layoutParams = lp
                countText.alpha = 0f
            }

            barContainer.post {
                val containerWidth = barContainer.width
                val targetWidth = (ratio * containerWidth).toInt().coerceAtLeast(if (item.count > 0) 24.dpToPx(itemView.context) else 0)

                if (shouldAnimate && item.count > 0) {
                    val animator = ValueAnimator.ofFloat(0f, targetWidth.toFloat())
                    animator.duration = 800L
                    animator.interpolator = OvershootInterpolator(1.5f)
                    animator.addUpdateListener { animation ->
                        val animatedValue = (animation.animatedValue as Float).toInt()
                        val animLp = barFill.layoutParams
                        animLp.width = animatedValue.coerceAtLeast(24.dpToPx(itemView.context))
                        barFill.layoutParams = animLp
                        countText.alpha = animation.animatedFraction
                    }
                    animator.start()
                } else {
                    val lp = barFill.layoutParams
                    lp.width = targetWidth
                    barFill.layoutParams = lp
                    countText.alpha = 1f
                }
            }

            updateSelectedState(position)

            itemView.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = position
                if (oldPosition >= 0 && oldPosition != position) {
                    notifyItemChanged(oldPosition, SELECTION_CHANGE)
                }
                notifyItemChanged(position, SELECTION_CHANGE)
                onMonthClick(item.month)
            }
        }

        fun updateSelectedState(position: Int) {
            if (position == selectedPosition) {
                val bg = PaintDrawable(Color.parseColor("#EFF6FF")).apply {
                    setCornerRadius(12f)
                }
                itemRoot.background = bg
                monthText.setTextColor(Color.parseColor("#2563EB"))
                monthText.setTypeface(monthText.typeface, android.graphics.Typeface.BOLD)
                countText.setTextColor(Color.parseColor("#1D4ED8"))
                countText.setTypeface(countText.typeface, android.graphics.Typeface.BOLD)
                barFill.animate().scaleY(1.1f).setDuration(200).start()
            } else {
                itemRoot.setBackgroundColor(Color.TRANSPARENT)
                monthText.setTextColor(Color.parseColor("#6B7280"))
                monthText.setTypeface(monthText.typeface, android.graphics.Typeface.NORMAL)
                countText.setTextColor(Color.parseColor("#374151"))
                countText.setTypeface(countText.typeface, android.graphics.Typeface.NORMAL)
                barFill.animate().scaleY(1f).setDuration(200).start()
            }
        }

        private fun lightenColor(color: Int, factor: Float): Int {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val newR = (r + (255 - r) * factor).toInt().coerceIn(0, 255)
            val newG = (g + (255 - g) * factor).toInt().coerceIn(0, 255)
            val newB = (b + (255 - b) * factor).toInt().coerceIn(0, 255)
            return Color.rgb(newR, newG, newB)
        }
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chart, parent, false)
        return ChartViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
        holder.bind(data[position], position)
    }

    override fun onBindViewHolder(holder: ChartViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(SELECTION_CHANGE)) {
            holder.updateSelectedState(position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = data.size

    fun updateData(newData: List<AnalysisFragment.ChartItem>, animate: Boolean = false) {
        data = newData
        maxCount = newData.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        shouldAnimate = animate
        selectedPosition = -1
        notifyDataSetChanged()
    }

    companion object {
        private const val SELECTION_CHANGE = "selection_change"
    }
}
