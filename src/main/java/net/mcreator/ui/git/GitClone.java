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

package net.mcreator.ui.git;

import net.mcreator.ui.dialogs.ProgressDialog;
import net.mcreator.ui.init.L10N;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Downloads a workspace from a GitHub repository (git clone) and opens it. This is the simplest way to get the mod
 * of someone else, no empty workspace is needed.
 */
public final class GitClone {

	private static final Logger LOG = LogManager.getLogger("Git clone");

	private record Result(int code, String output) {}

	public static void run(Window parent, Consumer<File> opener) {
		if (execute(null, "git", "--version").code() != 0) {
			JOptionPane.showMessageDialog(parent, L10N.t("git.no_git"), L10N.t("git.title"),
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		Object answer = JOptionPane.showInputDialog(parent, L10N.t("git.clone.message"), L10N.t("git.clone.title"),
				JOptionPane.QUESTION_MESSAGE, null, null, "https://github.com/");
		if (answer == null || answer.toString().isBlank())
			return;
		String url = answer.toString().trim();

		String name = url.replaceAll("/+$", "").replaceAll("\\.git$", "");
		name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
		if (name.isBlank())
			name = "workspace";

		File base = new File(System.getProperty("user.home"), "ModLabWorkspaces");
		if (!base.isDirectory() && !base.mkdirs())
			return;
		File target = new File(base, name);
		for (int i = 2; target.exists(); i++)
			target = new File(base, name + "-" + i);

		File folder = target;
		AtomicReference<Result> result = new AtomicReference<>();
		ProgressDialog dial = new ProgressDialog(parent, L10N.t("git.clone.title"));
		new Thread(() -> {
			ProgressDialog.ProgressUnit unit = new ProgressDialog.ProgressUnit(L10N.t("git.clone.title"));
			dial.addProgressUnit(unit);
			try {
				result.set(execute(base, "git", "clone", url, folder.getName()));
				unit.markStateOk();
			} catch (Exception e) {
				LOG.error("Clone failed", e);
				unit.markStateError();
			}
			dial.hideDialog();
		}, "GitClone").start();
		dial.setVisible(true);

		Result cloned = result.get();
		if (cloned == null || cloned.code() != 0) {
			JTextArea area = new JTextArea(cloned == null ? "" : cloned.output().trim());
			area.setEditable(false);
			area.setLineWrap(true);
			JScrollPane scroll = new JScrollPane(area);
			scroll.setPreferredSize(new Dimension(560, 220));
			JOptionPane.showMessageDialog(parent, scroll, L10N.t("git.clone.title"), JOptionPane.ERROR_MESSAGE);
			return;
		}

		File[] workspaces = folder.listFiles((dir, file) -> file.endsWith(".mcreator") && new File(dir, file).isFile());
		if (workspaces == null || workspaces.length == 0) {
			JOptionPane.showMessageDialog(parent, L10N.t("git.clone.no_workspace", folder.getAbsolutePath()),
					L10N.t("git.clone.title"), JOptionPane.WARNING_MESSAGE);
			return;
		}

		Arrays.sort(workspaces);
		opener.accept(workspaces[0]);
	}

	private static Result execute(File directory, String... command) {
		try {
			ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
			if (directory != null)
				builder.directory(directory);
			builder.environment().put("GIT_TERMINAL_PROMPT", "0");
			Process process = builder.start();
			process.getOutputStream().close();

			StringBuffer output = new StringBuffer();
			Thread reader = new Thread(() -> {
				try {
					output.append(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
				} catch (IOException ignored) {
				}
			}, "GitClone-reader");
			reader.start();

			if (!process.waitFor(10, TimeUnit.MINUTES)) {
				process.destroyForcibly();
				return new Result(-1, "Timeout");
			}
			reader.join(5000);
			return new Result(process.exitValue(), output.toString());
		} catch (IOException e) {
			return new Result(-2, command[0] + " not found: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Result(-3, "Interrupted");
		}
	}

}
