import { parseTime } from "./sgj";

/**
 * 表格时间格式化
 */
export function formatDate(cellValue: any): string {
	if (cellValue == null || cellValue == "") return "";
	const date = new Date(cellValue);
	const year = date.getFullYear();
	const month =
		date.getMonth() + 1 < 10
			? "0" + (date.getMonth() + 1)
			: date.getMonth() + 1;
	const day = date.getDate() < 10 ? "0" + date.getDate() : date.getDate();
	const hours = date.getHours() < 10 ? "0" + date.getHours() : date.getHours();
	const minutes =
		date.getMinutes() < 10 ? "0" + date.getMinutes() : date.getMinutes();
	const seconds =
		date.getSeconds() < 10 ? "0" + date.getSeconds() : date.getSeconds();
	return (
		year + "-" + month + "-" + day + " " + hours + ":" + minutes + ":" + seconds
	);
}

/**
 * @param time
 * @param option
 * @returns {string}
 */
export function formatTime(time: number | string, option?: string): string {
	if (("" + time).length === 10) {
		time = parseInt(time as string) * 1000;
	} else {
		time = +time;
	}
	const d = new Date(time);
	const now = Date.now();

	const diff = (now - d.getTime()) / 1000;

	if (diff < 30) {
		return "刚刚";
	} else if (diff < 3600) {
		// less 1 hour
		return Math.ceil(diff / 60) + "分钟前";
	} else if (diff < 3600 * 24) {
		return Math.ceil(diff / 3600) + "小时前";
	} else if (diff < 3600 * 24 * 2) {
		return "1天前";
	}
	if (option) {
		return parseTime(time, option) || "";
	} else {
		return (
			d.getMonth() +
			1 +
			"月" +
			d.getDate() +
			"日" +
			d.getHours() +
			"时" +
			d.getMinutes() +
			"分"
		);
	}
}

export function makeMap(
	str: string,
	expectsLowerCase?: boolean,
): (val: string) => boolean {
	const values = new Set(str.split(","));
	return (val: string) =>
		values.has(expectsLowerCase ? val.toLowerCase() : val);
}

export const beautifierConf = {
	html: {
		indent_size: "2",
		indent_char: " ",
		max_preserve_newlines: "-1",
		preserve_newlines: false,
		keep_array_indentation: false,
		break_chained_methods: false,
		indent_scripts: "separate",
		brace_style: "end-expand",
		space_before_conditional: true,
		unescape_strings: false,
		jslint_happy: false,
		end_with_newline: true,
		wrap_line_length: "110",
		indent_inner_html: true,
		comma_first: false,
		e4x: true,
		indent_empty_lines: true,
	},
	js: {
		indent_size: "2",
		indent_char: " ",
		max_preserve_newlines: "-1",
		preserve_newlines: false,
		keep_array_indentation: false,
		break_chained_methods: false,
		indent_scripts: "normal",
		brace_style: "end-expand",
		space_before_conditional: true,
		unescape_strings: false,
		jslint_happy: true,
		end_with_newline: true,
		wrap_line_length: "110",
		indent_inner_html: true,
		comma_first: false,
		e4x: true,
		indent_empty_lines: true,
	},
};

// 首字母大小
export function titleCase(str: string): string {
	return str.replace(/( |^)[a-z]/g, (L) => L.toUpperCase());
}

export function isNumberStr(str: string): boolean {
	return /^[+-]?(0|([1-9]\d*))(\.\d+)?$/g.test(str);
}
