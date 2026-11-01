/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2012-2020, Pylo
 * Copyright (C) 2020-2026, Pylo, opensource contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.ui.dialogs;

import net.mcreator.ui.init.L10N;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Explains the most common Java compilation errors in plain words (rules, no network needed).
 */
public final class CompileErrorExplainer {

	/**
	 * @return HTML text with at most 3 different explanations, or an empty string if no error is recognized
	 */
	public static String explain(String compilerOutput) {
		List<String> lines = compilerOutput.lines().map(String::strip).toList();
		Set<String> seen = new LinkedHashSet<>();
		List<String> items = new ArrayList<>();

		for (int i = 0; i < lines.size() && items.size() < 3; i++) {
			String line = lines.get(i);
			int index = line.indexOf(": error:");
			if (!line.contains(".java:") || index < 0)
				continue;

			String message = line.substring(index + ": error:".length()).strip();
			String key = classify(message);
			if (key == null)
				continue;

			String detail = "";
			if (key.equals("cannot_find_symbol")) { // the name is on one of the next lines
				for (int j = i + 1; j < Math.min(lines.size(), i + 4); j++) {
					if (lines.get(j).startsWith("symbol:")) {
						detail = lines.get(j).substring("symbol:".length()).strip();
						break;
					}
				}
			} else if (key.equals("package_missing")) {
				detail = message.replaceFirst("^package\\s+", "").replaceFirst("\\s+does not exist.*$", "");
			}

			if (seen.add(key + detail))
				items.add(L10N.t("compile_error." + key, escape(detail)));
		}

		if (items.isEmpty())
			return "";

		StringBuilder html = new StringBuilder("<html><br><b>").append(L10N.t("compile_error.title")).append("</b><ul>");
		for (String item : items)
			html.append("<li>").append(item).append("</li>");
		return html.append("</ul>").toString();
	}

	private static String classify(String message) {
		if (message.contains("cannot find symbol"))
			return "cannot_find_symbol";
		if (message.startsWith("package ") && message.contains("does not exist"))
			return "package_missing";
		if (message.contains("incompatible types"))
			return "incompatible_types";
		if (message.contains("expected") || message.contains("illegal start of") || message.contains("not a statement")
				|| message.contains("reached end of file while parsing"))
			return "syntax";
		if (message.contains("missing return statement"))
			return "missing_return";
		if (message.contains("is already defined in"))
			return "already_defined";
		if (message.contains("might not have been initialized"))
			return "not_initialized";
		if (message.contains("cannot be applied to given types") || message.contains("no suitable method found")
				|| message.contains("no suitable constructor found"))
			return "cannot_be_applied";
		if (message.contains("unreported exception"))
			return "unreported_exception";
		if (message.contains("non-static") && message.contains("static context"))
			return "non_static";
		if (message.contains("does not override or implement a method"))
			return "does_not_override";
		if (message.contains("duplicate class"))
			return "duplicate_class";
		return null;
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

}
