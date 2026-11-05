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

package net.mcreator.ui.laf;

import com.formdev.flatlaf.FlatLaf;
import net.mcreator.preferences.PreferencesManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.InputStream;

/**
 * Registers the Inter font (SIL Open Font License, see LICENSE-Inter.txt) that ModLab embeds, and asks FlatLaf to use it
 * as the font of the whole interface. It can be switched off in the preferences.
 */
public final class ModLabFonts {

	private static final Logger LOG = LogManager.getLogger("ModLab fonts");

	private static final String[] FILES = { "Inter-Regular.ttf", "Inter-Bold.ttf", "Inter-Italic.ttf",
			"Inter-BoldItalic.ttf" };

	public static void install() {
		if ("System".equals(PreferencesManager.PREFERENCES.ui.interfaceFont.get()))
			return;

		try {
			GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
			int registered = 0;
			for (String file : FILES) {
				try (InputStream in = ModLabFonts.class.getResourceAsStream("/net/mcreator/ui/fonts/" + file)) {
					if (in != null && environment.registerFont(Font.createFont(Font.TRUETYPE_FONT, in)))
						registered++;
				}
			}
			if (registered > 0)
				FlatLaf.setPreferredFontFamily("Inter");
		} catch (Exception e) {
			LOG.warn("Could not install the Inter font, the system font is used", e);
		}
	}

}
