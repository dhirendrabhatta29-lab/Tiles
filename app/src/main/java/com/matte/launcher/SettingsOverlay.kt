package com.matte.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Categorized Settings screen and First-Run Permissions Onboarding overlay.
 * Renders glassmorphic cards styled with the active theme and blur settings.
 */
internal class SettingsOverlay(
    private val activity: Activity,
    private val prefs: LauncherPrefs,
    private val callbacks: Callbacks
) : FrameLayout(activity) {

    interface Callbacks {
        fun themeColors(): ThemeColors
        fun useWallpaper(): Boolean
        fun solidActive(): Boolean
        fun customTilesActive(): Boolean
        fun onAppearanceChanged(rebuildLayout: Boolean)
        fun onLayoutConfigChanged()
        fun onDockConfigChanged()
        fun onUsageTilingChanged(enabled: Boolean)
        fun onOpenBackgroundMenu()
        fun onOpenSolidColorMenu()
        fun onToggleCustomTiles()
        fun onExportLayout()
        fun onImportLayout()
        fun onResetLayout()
        fun onCustomizeDock()
        fun onOpenMotionMenu()
        fun onClose()
    }

    private val density = activity.resources.displayMetrics.density
    private val scrollView = ScrollView(activity).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    private val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
    }
    private var onboardingMode = false
    private var isOpen = false
    private var permContainer: LinearLayout? = null

    val showing: Boolean get() = isOpen

    init {
        visibility = View.GONE
        isClickable = true
        isFocusable = true
        scrollView.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun open(onboarding: Boolean = false) {
        onboardingMode = onboarding
        isOpen = true
        rebuild()
        visibility = View.VISIBLE
        animate().cancel()
        alpha = 0f
        translationY = dp(28).toFloat()
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240L)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()
    }

    fun close(immediate: Boolean = false) {
        if (!isOpen && visibility != View.VISIBLE) return
        isOpen = false
        animate().cancel()
        if (immediate) {
            visibility = View.GONE
            alpha = 1f
            translationY = 0f
            callbacks.onClose()
        } else {
            animate()
                .alpha(0f)
                .translationY(dp(24).toFloat())
                .setDuration(180L)
                .withEndAction {
                    visibility = View.GONE
                    alpha = 1f
                    translationY = 0f
                    callbacks.onClose()
                }
                .start()
        }
    }

    fun refreshPermissions() {
        if (!isOpen) return
        val box = permContainer ?: return
        box.removeAllViews()
        populatePermissions(box, callbacks.themeColors())
    }

    fun rebuild() {
        val scrollY = scrollView.scrollY
        val theme = callbacks.themeColors()
        val blur = prefs.blurIntensity
        setBackgroundColor(BlurEffects.overlayScrim(theme.background, blur, callbacks.useWallpaper()))
        content.removeAllViews()
        content.setPadding(dp(16), dp(14), dp(16), dp(36))

        if (onboardingMode) {
            buildOnboarding(theme)
        } else {
            buildFullSettings(theme)
        }
        scrollView.post { scrollView.scrollTo(0, scrollY) }
    }

    private fun buildOnboarding(theme: ThemeColors) {
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(16), dp(4), dp(16))
        }
        header.addView(titleText("Welcome to Tiles", 26f, theme.text, bold = true))
        header.addView(
            bodyText(
                "Grant the permissions below for live notifications, weather, Material You themes, and smooth launcher gestures. You can change these anytime in Settings.",
                13.5f,
                theme.mutedText
            ).apply {
                setPadding(0, dp(8), 0, 0)
            }
        )
        content.addView(header)

        val card = sectionCard(theme)
        val pBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        permContainer = pBox
        populatePermissions(pBox, theme)
        card.addView(pBox)
        content.addView(card, sectionMargins())

        val doneBtn = actionButton("Continue to Home", theme[TileTone.ACCENT].background, theme[TileTone.ACCENT].foreground) {
            prefs.onboardingSeen = true
            close()
        }
        content.addView(doneBtn, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(50)).apply {
            topMargin = dp(20)
        })

        val openSettingsBtn = actionButton("Open Full Launcher Settings", theme.panel, theme.text) {
            prefs.onboardingSeen = true
            onboardingMode = false
            rebuild()
            scrollView.scrollTo(0, 0)
        }
        content.addView(openSettingsBtn, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(46)).apply {
            topMargin = dp(10)
        })
    }

    private fun buildFullSettings(theme: ThemeColors) {
        // Top Bar
        val topBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(6), dp(2), dp(14))
        }
        val titleCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(titleText("Tiles Settings", 24f, theme.text, bold = true))
        titleCol.addView(bodyText("Personalize tiles, blur, dock, animations & permissions", 12f, theme.mutedText))
        topBar.addView(titleCol, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val closeBtn = pillChip("Done", selected = true, theme = theme) { close() }
        topBar.addView(closeBtn)
        content.addView(topBar)

        // 1. Appearance & Theme
        content.addView(sectionHeader("APPEARANCE & THEME", theme))
        val appearance = sectionCard(theme)
        appearance.addView(bodyText("Color Scheme", 13f, theme.text, bold = true).apply {
            setPadding(0, 0, 0, dp(8))
        })
        appearance.addView(buildThemePicker(theme))
        appearance.addView(divider(theme))

        val mySubtitle = if (WallpaperThemeEngine.isMaterialYouSupported()) {
            "Adapt tiles & surfaces to Android 12+ wallpaper colors"
        } else {
            "Extract dynamic palette from system wallpaper (API 27+ fallback)"
        }
        appearance.addView(toggleRow("Material You Dynamic Colors", mySubtitle, prefs.materialYouEnabled, theme) { on ->
            prefs.materialYouEnabled = on
            if (on) prefs.solidMode = false
            callbacks.onAppearanceChanged(rebuildLayout = true)
            rebuild()
        })
        appearance.addView(divider(theme))

        appearance.addView(toggleRow(
            "Wallpaper Color Gradient",
            "Multi-stop gradient fills derived from your current wallpaper",
            prefs.wallpaperGradientEnabled,
            theme
        ) { on ->
            prefs.wallpaperGradientEnabled = on
            callbacks.onAppearanceChanged(rebuildLayout = true)
            rebuild()
        })
        appearance.addView(divider(theme))

        val blurSub = if (BlurEffects.isRealtimeBlurSupported()) {
            "Real-time RenderEffect & window blur behind overlays and dock"
        } else {
            "Translucent glassmorphism scrim (Android 12+ required for GPU blur)"
        }
        appearance.addView(sliderRow(
            title = "Blur Intensity",
            subtitle = blurSub,
            minVal = 0,
            maxVal = 100,
            current = prefs.blurIntensity,
            format = { "$it%" },
            theme = theme
        ) { value ->
            prefs.blurIntensity = value
            callbacks.onAppearanceChanged(rebuildLayout = false)
            setBackgroundColor(BlurEffects.overlayScrim(callbacks.themeColors().background, value, callbacks.useWallpaper()))
        })
        appearance.addView(divider(theme))

        appearance.addView(sliderRow(
            title = "Tile Corner Radius",
            subtitle = "Square (0dp) to OxygenOS rounded cards (28dp)",
            minVal = 0,
            maxVal = 28,
            current = prefs.tileCornerRadiusDp,
            format = { "${it}dp" },
            theme = theme
        ) { value ->
            prefs.tileCornerRadiusDp = value
            callbacks.onAppearanceChanged(rebuildLayout = true)
        })
        appearance.addView(divider(theme))

        appearance.addView(sliderRow(
            title = "Tile Spacing",
            subtitle = "Grid gap between tiles",
            minVal = 2,
            maxVal = 14,
            current = prefs.tileSpacingDp,
            format = { "${it}dp" },
            theme = theme
        ) { value ->
            prefs.tileSpacingDp = value
            callbacks.onLayoutConfigChanged()
        })
        appearance.addView(divider(theme))

        appearance.addView(buttonRow(
            theme,
            "Background" to { close(true); callbacks.onOpenBackgroundMenu() },
            "Solid Tile Color" to { close(true); callbacks.onOpenSolidColorMenu() }
        ))
        content.addView(appearance, sectionMargins())

        // 2. Tiles & Layout
        content.addView(sectionHeader("TILES & LAYOUT", theme))
        val tilesCard = sectionCard(theme)
        tilesCard.addView(chipGroupRow(
            title = "Grid Columns",
            subtitle = "Tiles across each home screen width",
            options = listOf("3 Cols" to 3, "4 Cols" to 4, "5 Cols" to 5),
            selectedValue = prefs.gridColumns,
            theme = theme
        ) { cols ->
            prefs.gridColumns = cols
            callbacks.onLayoutConfigChanged()
            rebuild()
        })
        tilesCard.addView(divider(theme))

        tilesCard.addView(chipGroupRow(
            title = "OxygenOS Large Tile Default",
            subtitle = "Default expanded size when toggling a tile to Large mode",
            options = listOf("2×2 Card" to "2x2", "2×1 Wide" to "2x1"),
            selectedValue = prefs.largeTileDefault,
            theme = theme
        ) { sizeKey ->
            prefs.largeTileDefault = sizeKey
            rebuild()
        })
        tilesCard.addView(divider(theme))

        tilesCard.addView(toggleRow(
            "Dynamic Live Tiles",
            "Show live media controls, weather, battery, calendar & notification snippets on tiles",
            prefs.dynamicTilesEnabled,
            theme
        ) { on ->
            prefs.dynamicTilesEnabled = on
            callbacks.onLayoutConfigChanged()
        })
        tilesCard.addView(divider(theme))

        val notifNote = if (PermissionsHelper.isNotificationAccessGranted(activity)) {
            "Smoothly elevate active-notification tiles to the top priority zone & restore on dismiss"
        } else {
            "Requires Notification Access (grant in Permissions below)"
        }
        tilesCard.addView(toggleRow(
            "Notification-Driven Tile Movement",
            notifNote,
            prefs.notificationMoveEnabled,
            theme
        ) { on ->
            prefs.notificationMoveEnabled = on
            callbacks.onLayoutConfigChanged()
        })
        tilesCard.addView(divider(theme))

        tilesCard.addView(toggleRow(
            "Usage-Based Tiling",
            if (PermissionsHelper.isUsageAccessGranted(activity))
                "Most-used apps get bigger tiles and move to the front. Saved layout is untouched; turn off to restore it."
            else "Needs Usage Access (see Permissions below). Until granted your manual layout is used.",
            prefs.usageTilingEnabled,
            theme
        ) { on ->
            prefs.usageTilingEnabled = on
            callbacks.onUsageTilingChanged(on)
            rebuild()
        })
        tilesCard.addView(divider(theme))

        tilesCard.addView(toggleRow(
            "Custom Tile Style Mode",
            "Long-press tiles to assign custom color or image (pauses move/resize while on)",
            callbacks.customTilesActive(),
            theme
        ) { _ ->
            callbacks.onToggleCustomTiles()
            rebuild()
        })
        tilesCard.addView(divider(theme))

        tilesCard.addView(buttonRow(
            theme,
            "Export Layout" to { close(true); callbacks.onExportLayout() },
            "Import Layout" to { close(true); callbacks.onImportLayout() },
            "Reset" to { close(true); callbacks.onResetLayout() }
        ))
        content.addView(tilesCard, sectionMargins())

        // 3. Dock & Search
        content.addView(sectionHeader("DOCK & SEARCH", theme))
        val dockCard = sectionCard(theme)
        dockCard.addView(toggleRow(
            "Persistent Bottom Dock",
            "Pinned glassmorphic dock below the tile grid for essential apps",
            prefs.dockVisible,
            theme
        ) { on ->
            prefs.dockVisible = on
            callbacks.onDockConfigChanged()
            rebuild()
        })
        dockCard.addView(divider(theme))

        dockCard.addView(chipGroupRow(
            title = "Dock Icon Count",
            subtitle = "Number of app slots pinned in the bottom dock",
            options = (4..10).map { "$it Apps" to it },
            selectedValue = prefs.dockCount,
            theme = theme
        ) { count ->
            prefs.dockCount = count
            callbacks.onDockConfigChanged()
            rebuild()
        })
        dockCard.addView(divider(theme))

        dockCard.addView(toggleRow(
            "Quick Keyboard Search Button",
            "Dedicated keyboard icon to open instant T9 & fuzzy app search with IME raised",
            prefs.quickKeyboardVisible,
            theme
        ) { on ->
            prefs.quickKeyboardVisible = on
            callbacks.onDockConfigChanged()
        })
        dockCard.addView(divider(theme))

        dockCard.addView(toggleRow(
            "Niagara-Style Alphabet Slider",
            "Right-edge scrub bar in All Apps for fast alphabetical jumping",
            prefs.alphabetSliderEnabled,
            theme
        ) { on ->
            prefs.alphabetSliderEnabled = on
        })
        dockCard.addView(divider(theme))

        dockCard.addView(buttonRow(
            theme,
            "Customize Dock Apps" to { close(true); callbacks.onCustomizeDock() }
        ))
        content.addView(dockCard, sectionMargins())

        // 4. Animations
        content.addView(sectionHeader("ANIMATIONS & TRANSITIONS", theme))
        val animCard = sectionCard(theme)
        animCard.addView(chipGroupRow(
            title = "App Open Transition",
            subtitle = "Physics-based morph from tile bounds or classic 3D tile turn",
            options = listOf(
                "Morph" to LauncherPrefs.LAUNCH_MORPH,
                "Tile Turn" to LauncherPrefs.LAUNCH_TURN,
                "Slow Expand + Dock Return" to LauncherPrefs.LAUNCH_EXPAND,
                "Slide" to LauncherPrefs.LAUNCH_SLIDE,
                "None" to LauncherPrefs.LAUNCH_NONE
            ),
            selectedValue = prefs.launchStyle,
            theme = theme
        ) { style ->
            prefs.launchStyle = style
            rebuild()
        })
        animCard.addView(divider(theme))

        animCard.addView(sliderRow(
            title = "Launch Animation Speed",
            subtitle = "Faster (140ms) to smoother dramatic morph (560ms)",
            minVal = 140,
            maxVal = 560,
            current = prefs.launchSpeedMs,
            format = { "${it}ms" },
            theme = theme
        ) { ms ->
            prefs.launchSpeedMs = ms
        })
        animCard.addView(divider(theme))

        animCard.addView(toggleRow(
            "Smooth Recents & Home Return",
            "Damped return settlement & predictive back without jarring layout jumps",
            prefs.recentsSmoothEnabled,
            theme
        ) { on ->
            prefs.recentsSmoothEnabled = on
        })
        animCard.addView(divider(theme))

        animCard.addView(toggleRow(
            "Double-Tap Empty Space to Lock",
            "Uses the optional Accessibility Service to lock the screen smoothly",
            prefs.doubleTapLockEnabled,
            theme
        ) { on ->
            prefs.doubleTapLockEnabled = on
        })
        animCard.addView(divider(theme))

        animCard.addView(buttonRow(
            theme,
            "Advanced Motion & Lock Preview" to { close(true); callbacks.onOpenMotionMenu() }
        ))
        content.addView(animCard, sectionMargins())

        // 5. Permissions & Smooth Experience
        content.addView(sectionHeader("PERMISSIONS & SMOOTH EXPERIENCE", theme))
        val permsCard = sectionCard(theme)
        val pBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        permContainer = pBox
        populatePermissions(pBox, theme)
        permsCard.addView(pBox)
        content.addView(permsCard, sectionMargins())
    }

    private fun buildThemePicker(theme: ThemeColors): View {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val activeName = prefs.colorTheme
        val useCustomTheme = !prefs.materialYouEnabled && !callbacks.solidActive()
        for (entry in LauncherTheme.entries) {
            val isSelected = useCustomTheme && entry.name == activeName
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply {
                    setColor(entry.colors.panel)
                    cornerRadius = dp(12).toFloat()
                    val strokeColor = if (isSelected) theme[TileTone.ACCENT].background else 0x33FFFFFF
                    setStroke(if (isSelected) dp(2) else 1, strokeColor)
                }
                setOnClickListener {
                    prefs.colorTheme = entry.name
                    prefs.materialYouEnabled = false
                    prefs.solidMode = false
                    callbacks.onAppearanceChanged(rebuildLayout = true)
                    rebuild()
                }
            }
            val dots = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val tones = listOf(TileTone.PAPER, TileTone.CHARCOAL, TileTone.SECONDARY, TileTone.ACCENT, TileTone.SIGNAL)
            for ((idx, tone) in tones.withIndex()) {
                val dot = View(activity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(entry.colors[tone].background)
                    }
                }
                dots.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                    if (idx > 0) marginStart = dp(4)
                })
            }
            item.addView(dots)
            item.addView(TextView(activity).apply {
                text = if (isSelected) "${entry.label} ✓" else entry.label
                setTextColor(entry.colors.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setSingleLine()
                setPadding(0, dp(6), 0, 0)
            })
            row.addView(item, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(8)
            })
        }
        return HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }
    }

    private fun populatePermissions(container: LinearLayout, theme: ThemeColors) {
        val statuses = PermissionsHelper.allStatuses(activity)
        for ((index, status) in statuses.withIndex()) {
            if (index > 0) container.addView(divider(theme))
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val top = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(bodyText(status.title, 14f, theme.text, bold = true),
                LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

            val badgeBg = if (status.granted) 0xFF2E7D32.toInt()
            else if (status.optional) 0xFF6B7280.toInt()
            else 0xFFC65D1A.toInt()
            val badgeText = if (status.granted) "GRANTED" else if (status.optional) "OPTIONAL" else "MISSING"
            val badge = TextView(activity).apply {
                text = badgeText
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = GradientDrawable().apply {
                    setColor(badgeBg)
                    cornerRadius = dp(10).toFloat()
                }
            }
            top.addView(badge)
            row.addView(top)

            row.addView(bodyText(status.reason, 12f, theme.mutedText).apply {
                setPadding(0, dp(4), 0, dp(8))
            })

            val btnLabel = if (status.granted) "Manage" else status.actionLabel
            val btnFill = if (status.granted) theme[TileTone.CHARCOAL].background else theme[TileTone.ACCENT].background
            val btnInk = if (status.granted) theme[TileTone.CHARCOAL].foreground else theme[TileTone.ACCENT].foreground
            val btn = TextView(activity).apply {
                text = btnLabel
                setTextColor(btnInk)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = GradientDrawable().apply {
                    setColor(btnFill)
                    cornerRadius = dp(10).toFloat()
                }
                setOnClickListener { PermissionsHelper.request(activity, status.kind) }
            }
            row.addView(btn, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            container.addView(row)
        }
    }

    private fun toggleRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        theme: ThemeColors,
        onToggle: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val textCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(bodyText(title, 14f, theme.text, bold = true))
        textCol.addView(bodyText(subtitle, 12f, theme.mutedText).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(textCol, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(12)
        })
        val sw = Switch(activity).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        return row
    }

    private fun sliderRow(
        title: String,
        subtitle: String,
        minVal: Int,
        maxVal: Int,
        current: Int,
        format: (Int) -> String,
        theme: ThemeColors,
        onChanged: (Int) -> Unit
    ): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(bodyText(title, 14f, theme.text, bold = true),
            LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val valView = bodyText(format(current), 13f, theme[TileTone.ACCENT].background, bold = true)
        top.addView(valView)
        box.addView(top)
        box.addView(bodyText(subtitle, 12f, theme.mutedText).apply { setPadding(0, dp(2), 0, dp(6)) })

        val seek = SeekBar(activity).apply {
            max = maxVal - minVal
            progress = (current - minVal).coerceIn(0, maxVal - minVal)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, p: Int, fromUser: Boolean) {
                    val value = minVal + p
                    valView.text = format(value)
                    if (fromUser) onChanged(value)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        box.addView(seek, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        return box
    }

    private fun <T> chipGroupRow(
        title: String,
        subtitle: String,
        options: List<Pair<String, T>>,
        selectedValue: T,
        theme: ThemeColors,
        onSelect: (T) -> Unit
    ): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        box.addView(bodyText(title, 14f, theme.text, bold = true))
        box.addView(bodyText(subtitle, 12f, theme.mutedText).apply { setPadding(0, dp(2), 0, dp(8)) })
        val chips = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        for ((idx, opt) in options.withIndex()) {
            val chip = pillChip(opt.first, opt.second == selectedValue, theme) { onSelect(opt.second) }
            chips.addView(chip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                if (idx > 0) marginStart = dp(8)
            })
        }
        box.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(chips)
        })
        return box
    }

    private fun buttonRow(theme: ThemeColors, vararg buttons: Pair<String, () -> Unit>): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }
        for ((idx, pair) in buttons.withIndex()) {
            val btn = actionButton(pair.first, theme[TileTone.CHARCOAL].background, theme[TileTone.CHARCOAL].foreground, pair.second)
            row.addView(btn, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                if (idx > 0) marginStart = dp(8)
            })
        }
        return row
    }

    private fun actionButton(label: String, bg: Int, fg: Int, onClick: () -> Unit): TextView = TextView(activity).apply {
        text = label
        setTextColor(fg)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(10), 0, dp(10), 0)
        background = GradientDrawable().apply {
            setColor(bg)
            cornerRadius = dp(12).toFloat()
        }
        setOnClickListener { onClick() }
    }

    private fun pillChip(label: String, selected: Boolean, theme: ThemeColors, onClick: () -> Unit): TextView {
        val bg = if (selected) theme[TileTone.ACCENT].background else theme[TileTone.CHARCOAL].background
        val fg = if (selected) theme[TileTone.ACCENT].foreground else theme[TileTone.CHARCOAL].foreground
        return TextView(activity).apply {
            text = label
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", if (selected) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER
            setSingleLine()
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun sectionHeader(text: String, theme: ThemeColors): TextView = TextView(activity).apply {
        this.text = text
        setTextColor(theme.mutedText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        letterSpacing = 0.14f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setPadding(dp(4), dp(16), dp(4), dp(8))
    }

    private fun sectionCard(theme: ThemeColors): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = BlurEffects.glassBackground(
            baseColor = theme.panel,
            blurIntensity = prefs.blurIntensity,
            cornerRadiusPx = dp(18).toFloat(),
            isDark = theme.isDark,
            strongerScrim = true
        )
    }

    private fun sectionMargins(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4)
        }

    private fun divider(theme: ThemeColors): View = View(activity).apply {
        setBackgroundColor(if (theme.isDark) 0x1FFFFFFF else 0x18000000)
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, max(1, dp(1))).apply {
            topMargin = dp(10)
            bottomMargin = dp(10)
        }
    }

    private fun titleText(text: String, sp: Float, color: Int, bold: Boolean = false): TextView = TextView(activity).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun bodyText(text: String, sp: Float, color: Int, bold: Boolean = false): TextView = TextView(activity).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()
}
