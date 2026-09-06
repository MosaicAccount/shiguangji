// 处理主题样式：亮色主色 #A85F52，暗色按设计稿 v3.3 用提亮灰陶红 #C27A6D
export function handleThemeStyle(theme: string): void {
	const isDark =
		typeof document !== "undefined" &&
		document.documentElement.classList.contains("dark");
	const primary = isDark ? "#C27A6D" : theme;
	document.documentElement.style.setProperty("--el-color-primary", primary);
	for (let i = 1; i <= 9; i++) {
		document.documentElement.style.setProperty(
			`--el-color-primary-light-${i}`,
			`${getLightColor(primary, i / 10)}`,
		);
	}
	for (let i = 1; i <= 9; i++) {
		document.documentElement.style.setProperty(
			`--el-color-primary-dark-${i}`,
			`${getDarkColor(primary, i / 10)}`,
		);
	}
}

/**
 * 日夜模式切换动画：以点击点为圆心的圆形扩散（View Transition）。
 * 浏览器不支持或用户偏好减弱动效时退化为直接切换。
 *
 * @param toggle 实际执行切换的回调（如 settingsStore.toggleTheme）
 * @param event  触发点击事件（动画圆心取点击坐标）
 */
export async function animateThemeToggle(
	toggle: () => void,
	event?: MouseEvent,
): Promise<void> {
	const x = event?.clientX || window.innerWidth / 2;
	const y = event?.clientY || window.innerHeight / 2;
	const wasDark =
		typeof document !== "undefined" &&
		document.documentElement.classList.contains("dark");

	const isReducedMotion = window.matchMedia(
		"(prefers-reduced-motion: reduce)",
	).matches;
	const isSupported =
		typeof (document as any).startViewTransition === "function" &&
		!isReducedMotion;

	if (!isSupported) {
		toggle();
		return;
	}

	try {
		const transition = document.startViewTransition(async () => {
			await new Promise((resolve) => setTimeout(resolve, 10));
			toggle();
			await nextTick();
		});
		await transition.ready;

		const endRadius = Math.hypot(
			Math.max(x, window.innerWidth - x),
			Math.max(y, window.innerHeight - y),
		);
		const clipPath = [
			`circle(0px at ${x}px ${y}px)`,
			`circle(${endRadius}px at ${x}px ${y}px)`,
		];
		document.documentElement.animate(
			{
				clipPath: !wasDark ? [...clipPath].reverse() : clipPath,
			},
			{
				duration: 650,
				easing: "cubic-bezier(0.4, 0, 0.2, 1)",
				fill: "forwards",
				pseudoElement: !wasDark
					? "::view-transition-old(root)"
					: "::view-transition-new(root)",
			},
		);
		await transition.finished;
	} catch (error) {
		console.warn(
			"View transition failed, falling back to immediate toggle:",
			error,
		);
		toggle();
	}
}

/** 混合两种十六进制颜色 */
export function mixHexColors(fg: string, bg: string, t: number): string {
	const a = hexToRgb(String(fg).replace("#", ""));
	const b = hexToRgb(String(bg).replace("#", ""));
	const out = [0, 1, 2].map((i) => Math.round(a[i] * (1 - t) + b[i] * t));
	return rgbToHex(out[0], out[1], out[2]);
}

/** 暗色模式下柔化主题色 */
export function softenPrimaryForDark(theme: string): string {
	return mixHexColors(theme, "#2d3036", 0.34);
}

// hex颜色转rgb颜色
export function hexToRgb(str: string): number[] {
	str = str.replace("#", "");
	const hexs = str.match(/../g) || [];
	for (let i = 0; i < 3; i++) {
		hexs[i] = String(parseInt(hexs[i], 16));
	}
	return hexs.map((h) => parseInt(h));
}

// rgb颜色转Hex颜色
export function rgbToHex(r: number, g: number, b: number): string {
	const hexs = [r.toString(16), g.toString(16), b.toString(16)];
	for (let i = 0; i < 3; i++) {
		if (hexs[i].length == 1) {
			hexs[i] = `0${hexs[i]}`;
		}
	}
	return `#${hexs.join("")}`;
}

// 变浅颜色值
export function getLightColor(color: string, level: number): string {
	const rgb = hexToRgb(color);
	for (let i = 0; i < 3; i++) {
		rgb[i] = Math.floor((255 - rgb[i]) * level + rgb[i]);
	}
	return rgbToHex(rgb[0], rgb[1], rgb[2]);
}

// 变深颜色值
export function getDarkColor(color: string, level: number): string {
	const rgb = hexToRgb(color);
	for (let i = 0; i < 3; i++) {
		rgb[i] = Math.floor(rgb[i] * (1 - level));
	}
	return rgbToHex(rgb[0], rgb[1], rgb[2]);
}
