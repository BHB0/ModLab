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

package net.mcreator.ui.favorites;

import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.IElement;
import net.mcreator.workspace.elements.ModElement;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.function.Supplier;

/**
 * Draws a star on the favorite elements of the workspace list, on top of whichever view is used.
 */
public final class FavoriteStarRenderer implements ListCellRenderer<IElement> {

	private final ListCellRenderer<? super IElement> delegate;
	private final Supplier<Workspace> workspace;
	private final Overlay overlay = new Overlay();

	public FavoriteStarRenderer(ListCellRenderer<? super IElement> delegate, Supplier<Workspace> workspace) {
		this.delegate = delegate;
		this.workspace = workspace;
	}

	@SuppressWarnings("unchecked") @Override
	public Component getListCellRendererComponent(JList<? extends IElement> list, IElement value, int index,
			boolean isSelected, boolean cellHasFocus) {
		Component component = ((ListCellRenderer<IElement>) delegate).getListCellRendererComponent(
				(JList<IElement>) list, value, index, isSelected, cellHasFocus);

		if (value instanceof ModElement element && FavoritesManager.isFavorite(workspace.get(), element.getName())) {
			overlay.setContent(component);
			return overlay;
		}
		return component;
	}

	private static final class Overlay extends JPanel {

		Overlay() {
			super(null);
			setOpaque(false);
		}

		void setContent(Component component) {
			removeAll();
			add(component);
		}

		@Override public Dimension getPreferredSize() {
			return getComponentCount() > 0 ? getComponent(0).getPreferredSize() : super.getPreferredSize();
		}

		@Override public void doLayout() {
			if (getComponentCount() > 0)
				getComponent(0).setBounds(0, 0, getWidth(), getHeight());
		}

		@Override protected void paintChildren(Graphics g) {
			super.paintChildren(g);

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			double size = 7, cx = getWidth() - 12, cy = 12;
			Path2D star = new Path2D.Double();
			for (int i = 0; i < 10; i++) {
				double radius = i % 2 == 0 ? size : size * 0.42;
				double angle = -Math.PI / 2 + i * Math.PI / 5;
				double x = cx + radius * Math.cos(angle), y = cy + radius * Math.sin(angle);
				if (i == 0)
					star.moveTo(x, y);
				else
					star.lineTo(x, y);
			}
			star.closePath();
			g2.setColor(new Color(0, 0, 0, 120));
			g2.setStroke(new BasicStroke(2.4f));
			g2.draw(star);
			g2.setColor(new Color(0xFFB405));
			g2.fill(star);
			g2.dispose();
		}

	}

}
