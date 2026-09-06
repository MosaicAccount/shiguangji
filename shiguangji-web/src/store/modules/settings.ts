import defaultSettings from "@/settings";
import { useDark, useToggle } from "@vueuse/core";
import { useDynamicTitle } from "@/utils/dynamicTitle";
import { handleThemeStyle } from "@/utils/theme";

const isDark = useDark();
const toggleDark = useToggle(isDark);

const {
	showSettings,
	tagsView,
	tagsViewPersist,
	tagsIcon,
	tagsViewStyle,
	fixedHeader,
	sidebarLogo,
	dynamicTitle,
	footerVisible,
	footerContent,
} = defaultSettings;

interface LayoutSetting {
	theme?: string;
	tagsView?: boolean;
	tagsViewPersist?: boolean;
	tagsViewStyle?: string;
	tagsIcon?: boolean;
	fixedHeader?: boolean;
	sidebarLogo?: boolean;
	dynamicTitle?: boolean;
	footerVisible?: boolean;
}

// localStorage 可能被旧版本写入非法 JSON，解析失败时回退空对象
function readLayoutSetting(): LayoutSetting {
	try {
		const raw = JSON.parse(localStorage.getItem("layout-setting") || "{}");
		return raw && typeof raw === "object" ? (raw as LayoutSetting) : {};
	} catch {
		return {};
	}
}

const storageSetting = readLayoutSetting();

interface SettingsState {
	title: string;
	theme: string;
	showSettings: boolean;
	tagsView: boolean;
	tagsViewPersist: boolean;
	tagsViewStyle: string;
	tagsIcon: boolean;
	fixedHeader: boolean;
	sidebarLogo: boolean;
	dynamicTitle: boolean;
	footerVisible: boolean;
	footerContent: string;
	isDark: boolean;
}

const useSettingsStore = defineStore("settings", {
	state: (): SettingsState => ({
		title: "",
		//  品牌化：默认主色从 RuoYi 蓝 #409EFF 切到设计稿 v3.3 灰陶红 #A85F52
		// （老用户 localStorage 中旧 theme 值仍会覆盖默认值，可接受）
		theme: storageSetting.theme || "#A85F52",
		showSettings: showSettings,
		tagsView:
			storageSetting.tagsView === undefined
				? tagsView
				: storageSetting.tagsView,
		tagsViewPersist:
			storageSetting.tagsViewPersist === undefined
				? tagsViewPersist
				: storageSetting.tagsViewPersist,
		tagsIcon:
			storageSetting.tagsIcon === undefined
				? tagsIcon
				: storageSetting.tagsIcon,
		tagsViewStyle:
			storageSetting.tagsViewStyle === undefined
				? tagsViewStyle
				: storageSetting.tagsViewStyle,
		fixedHeader:
			storageSetting.fixedHeader === undefined
				? fixedHeader
				: storageSetting.fixedHeader,
		sidebarLogo:
			storageSetting.sidebarLogo === undefined
				? sidebarLogo
				: storageSetting.sidebarLogo,
		dynamicTitle:
			storageSetting.dynamicTitle === undefined
				? dynamicTitle
				: storageSetting.dynamicTitle,
		footerVisible:
			storageSetting.footerVisible === undefined
				? footerVisible
				: storageSetting.footerVisible,
		footerContent: footerContent,
		isDark: isDark.value,
	}),
	actions: {
		// 修改布局设置
		changeSetting(data: { key: string; value: any }) {
			const { key, value } = data;
			if (Object.hasOwn(this, key)) {
				(this as any)[key] = value;
			}
		},
		// 设置网页标题
		setTitle(title: string) {
			this.title = title;
			useDynamicTitle();
		},
		// 切换暗黑模式
		toggleTheme() {
			this.isDark = !this.isDark;
			toggleDark();
			nextTick(() => {
				handleThemeStyle(this.theme);
			});
		},
	},
});

export default useSettingsStore;
