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

package net.mcreator.ui.assistant;

import net.mcreator.ui.MCreator;

/**
 * Entry points of the assistant: it opens in the dock on the right of the workspace window.
 */
public final class AssistantDialog {

	public static void open(MCreator mcreator) {
		mcreator.showAssistant(null);
	}

	public static void openWith(MCreator mcreator, String message) {
		mcreator.showAssistant(message);
	}

}
