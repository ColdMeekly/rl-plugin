/*
 * Copyright (c) 2020, Belieal <https://github.com/Belieal>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.flippingutilities.ui.statistics;

import com.flippingutilities.controller.FlippingPlugin;
import com.flippingutilities.model.FlippingItem;
import com.flippingutilities.model.OfferEvent;
import com.flippingutilities.ui.uiutilities.*;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ComboBoxListRenderer;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

@Slf4j
public class StatsPanel extends JPanel
{
	private static final String[] TIME_INTERVAL_STRINGS = {"-1h (Past Hour)", "-4h (Past 4 Hours)", "-12h (Past 12 Hours)", "-1d (Past Day)", "-1w (Past Week)", "-1m (Past Month)", "Session", "All"};
	private static final String[] SORT_BY_STRINGS = {"Most Recent", "Most Total Profit", "Most Profit Each", "Highest ROI", "Highest Quantity"};
	private static final Dimension ICON_SIZE = new Dimension(16, 16);

	private static final Font BIG_PROFIT_FONT = StyleContext.getDefaultStyleContext()
		.getFont(FontManager.getRunescapeBoldFont().getName(), Font.PLAIN, 28);

	private FlippingPlugin plugin;
	private ItemManager itemManager;

	//Holds the sub info labels.
	private JPanel subInfoPanel;

	private JPanel statItemPanelsContainer = new JPanel();

	//Combo box that selects the time interval that startOfInterval contains.
	private JComboBox<String> timeIntervalDropdown = this.createTimeIntervalDropdown();
	private JComboBox<String> sortDropdown = this.createSortDropdown();

	//Represents the total profit made in the selected time interval.
	private JLabel totalProfitVal = new JLabel();

	/* Subinfo text labels */
	private final JLabel hourlyProfitText = new JLabel("Hourly Profit: ");
	private final JLabel roiText = new JLabel("ROI: ");
	private final JLabel totalFlipsText = new JLabel("Total Flips Made: ");
	private final JLabel mostCommonFlipText = new JLabel("Most Common: ");
	private final JLabel sessionTimeText = new JLabel("Session Time: ");
	private final JLabel[] textLabelArray = {hourlyProfitText, roiText, totalFlipsText, mostCommonFlipText, sessionTimeText};

	/* Subinfo value labels */
	private final JLabel hourlyProfitVal = new JLabel("", SwingConstants.RIGHT);
	private final JLabel roiVal = new JLabel("", SwingConstants.RIGHT);
	private final JLabel totalFlipsVal = new JLabel("", SwingConstants.RIGHT);
	private final JLabel mostCommonFlipVal = new JLabel("", SwingConstants.RIGHT);
	private final JLabel sessionTimeVal = new JLabel("", SwingConstants.RIGHT);
	private final JLabel[] valLabelArray = {hourlyProfitVal, roiVal, totalFlipsVal, mostCommonFlipVal, sessionTimeVal};

	private final JPanel hourlyProfitPanel = new JPanel(new BorderLayout());
	private final JPanel roiPanel = new JPanel(new BorderLayout());
	private final JPanel totalFlipsPanel = new JPanel(new BorderLayout());
	private final JPanel mostCommonFlipPanel = new JPanel(new BorderLayout());
	private final JPanel sessionTimePanel = this.createSessionTimePanel();
	private final JPanel[] subInfoPanelArray = {hourlyProfitPanel, roiPanel, totalFlipsPanel, mostCommonFlipPanel, sessionTimePanel};

	private long totalProfit;
	private long totalExpenses;
	private int totalFlips;
	private String mostCommonItemName;
	private int mostFlips;

	//Contains the unix time of the start of the interval.
	@Getter
	private Instant startOfInterval;
	@Getter
	private String startOfIntervalName = "Session";

	@Getter
	private String selectedSort;

	private ArrayList<StatItemPanel> activePanels = new ArrayList<>();

	@Getter
	private Set<String> expandedItems = new HashSet<>();
	@Getter
	private Set<String> expandedTradeHistories = new HashSet<>();
	@Getter
	private Set<Integer> itemsWithOffersTabSelected = new HashSet<>();

	private Paginator paginator;
	private ScheduledExecutorService executor;

	/**
	 * The statistics panel shows various stats about trades the user has made over a selectable time interval.
	 * This represents the front-end Statistics Tab.
	 * It is shown when it has been selected by the tab manager.
	 *
	 * @param plugin      Used to access the config and list of trades.
	 * @param itemManager Accesses the RuneLite item cache.
	 */
	public StatsPanel(final FlippingPlugin plugin, final ItemManager itemManager, ScheduledExecutorService executor)
	{
		super(false);
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.executor = executor;
		this.startOfInterval = plugin.viewStartOfSessionForCurrentView();
		this.prepareLabels();

		JPanel middlePanel = new JPanel(new BorderLayout());
		middlePanel.add(this.createProfitAndSubInfoContainer(), BorderLayout.NORTH);
		middlePanel.add(this.createSortAndScrollContainer(), BorderLayout.CENTER);

		setLayout(new BorderLayout());
		add(this.createTopPanel(), BorderLayout.NORTH);
		add(middlePanel, BorderLayout.CENTER);
		add(this.createPaginator(), BorderLayout.SOUTH);
		setBorder(new EmptyBorder(5,7,0,7));
	}

	/**
	 * Removes old stat items and builds new ones based on the passed trade list.
	 * Items are initialized with their sub info containers collapsed.
	 *
	 * @param flippingItems The list of flipping items that get shown on the stat panel.
	 */
	public void rebuild(List<FlippingItem> flippingItems)
	{
		//Remove old stats
		activePanels = new ArrayList<>();

		SwingUtilities.invokeLater(() ->
		{
			rebuildStatItemContainer(flippingItems);
			updateDisplays(flippingItems);
			revalidate();
			repaint();
		});
	}

	public void rebuildStatItemContainer(List<FlippingItem> flippingItems)
	{
		activePanels.clear();
		statItemPanelsContainer.removeAll();
		List<FlippingItem> sortedItems = sortTradeList(flippingItems);
		List<FlippingItem> itemsThatShouldHavePanels = sortedItems.stream().filter(item -> item.getIntervalHistory(startOfInterval).stream().anyMatch(OfferEvent::isValidOfferEvent)).collect(Collectors.toList());
		sortDropdown.setVisible(itemsThatShouldHavePanels.size() > 0);
		paginator.updateTotalPages(itemsThatShouldHavePanels.size());
		List<FlippingItem> itemsOnCurrentPage = paginator.getCurrentPageItems(itemsThatShouldHavePanels);
		List<StatItemPanel> newPanels = itemsOnCurrentPage.stream().map(item -> new StatItemPanel(plugin, itemManager, item)).collect(Collectors.toList());
		UIUtilities.stackPanelsVertically((List) newPanels, statItemPanelsContainer, 5);
		activePanels.addAll(newPanels);
	}

	private void updateSearch(IconTextField searchBar)
	{
		String lookup = searchBar.getText().toLowerCase();

		//Just so we don't mess with the highlight.

		//When the clear button is pressed, this is run.
		if (Strings.isNullOrEmpty(lookup))
		{
			rebuild(plugin.viewTradesForCurrentView());
			return;
		}

		ArrayList<FlippingItem> result = new ArrayList<>();
		for (FlippingItem item : plugin.viewTradesForCurrentView())
		{
			//Contains makes it a little more forgiving when searching.
			if (item.getItemName().toLowerCase().contains(lookup))
			{
				result.add(item);
			}
		}

		if (result.isEmpty())
		{
			searchBar.setIcon(IconTextField.Icon.ERROR);
			rebuild(plugin.viewTradesForCurrentView());
			return;
		}

		searchBar.setIcon(IconTextField.Icon.SEARCH);
		rebuild(result);
	}

	private JComboBox createTimeIntervalDropdown() {
		JComboBox<String> timeIntervalDropdown = new JComboBox<>(TIME_INTERVAL_STRINGS);
		timeIntervalDropdown.setRenderer(new ComboBoxListRenderer());
		timeIntervalDropdown.setEditable(true);
		timeIntervalDropdown.setBorder(BorderFactory.createMatteBorder(1,1,1,1, ColorScheme.DARKER_GRAY_COLOR.darker()));
		timeIntervalDropdown.setBackground(CustomColors.DARK_GRAY_LIGHTER);
		//setting the selected item as session before the item listener is attached so it doesn't fire a rebuild.
		timeIntervalDropdown.setSelectedItem("Session");
		timeIntervalDropdown.setToolTipText("Specify the time span you would like to see the statistics of");
		timeIntervalDropdown.addItemListener(event ->
		{
			if (event.getStateChange() == ItemEvent.SELECTED)
			{
				String interval = (String) event.getItem();
				if (interval == null) {
					return;
				}
				//remove the helper text. so something like "1w (Past week)" becomes just "1w"
				String justTheInterval = interval.split(" \\(")[0];
				ItemListener[] itemListeners = timeIntervalDropdown.getItemListeners();
				//have to remove item listeners so setSelectedItem doesn't cause another rebuild.
				for (ItemListener listener : itemListeners) {
					timeIntervalDropdown.removeItemListener(listener);
				}
				timeIntervalDropdown.setSelectedItem(justTheInterval);
				for (ItemListener itemListener : itemListeners) {
					timeIntervalDropdown.addItemListener(itemListener);
				}
				setTimeInterval(justTheInterval);
			}
		});
		return timeIntervalDropdown;
	}

	/**
	 * Updates the display of the total profit value along with the display of sub panels
	 *
	 * @param tradesList
	 */
	public void updateDisplays(List<FlippingItem> tradesList)
	{
		subInfoPanel.removeAll();
		for (JPanel panel : subInfoPanelArray)
		{
			panel.setBorder(new EmptyBorder(4, 2, 4, 2));
			subInfoPanel.add(panel);
			panel.setBackground(CustomColors.DARK_GRAY);
		}

		if (!Objects.equals(timeIntervalDropdown.getSelectedItem(), "Session"))
		{
			subInfoPanel.remove(sessionTimePanel);
			subInfoPanel.remove(hourlyProfitPanel);
		}

		totalProfit = 0;
		totalExpenses = 0;
		totalFlips = 0;
		mostCommonItemName = null;
		mostFlips = 0;

		for (FlippingItem item : tradesList)
		{
			List<OfferEvent> intervalHistory = item.getIntervalHistory(startOfInterval);
			if (intervalHistory.isEmpty())
			{
				continue;
			}
			totalProfit += item.currentProfit(intervalHistory);
			totalExpenses += item.getFlippedCashFlow(startOfInterval, true);
			int flips = item.getFlips(startOfInterval).size();
			totalFlips += flips;
			if (mostCommonItemName == null || mostFlips < flips)
			{
				mostFlips = flips;
				mostCommonItemName = item.getItemName();
			}
		}

		updateTotalProfitDisplay();
		updateSubInfoFont();
		if (Objects.equals(timeIntervalDropdown.getSelectedItem(), "Session"))
		{
			Duration accumulatedTime = plugin.viewAccumulatedTimeForCurrentView();
			updateSessionTimeDisplay(accumulatedTime);
			updateHourlyProfitDisplay(accumulatedTime);
		}
		updateRoiDisplay();
		updateTotalFlipsDisplay();
		updateMostCommonFlip();
	}

	/**
	 * Responsible for updating the total profit label at the very top.
	 * Sets the new total profit value from the items in tradesList from {@link FlippingPlugin#getTradesForCurrentView()}.
	 */
	private void updateTotalProfitDisplay()
	{
		if (plugin.viewTradesForCurrentView() == null)
		{
			totalProfitVal.setText("0");
			totalProfitVal.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
			totalProfitVal.setToolTipText("Total Profit: 0 gp");
			return;
		}

		totalProfitVal.setText(((totalProfit >= 0) ? "" : "-") + UIUtilities.quantityToRSDecimalStack(Math.abs(totalProfit), true) + " gp");
		totalProfitVal.setToolTipText("Total Profit: " + QuantityFormatter.formatNumber(totalProfit) + " gp");

		//Reproduce the RuneScape stack size colors
		if (totalProfit < 0)
		{
			//]-inf, 0[
			totalProfitVal.setForeground(CustomColors.OUTDATED_COLOR);
		}
		else if (totalProfit <= 100000)
		{
			//[0,100k)
			totalProfitVal.setForeground(ColorScheme.GRAND_EXCHANGE_ALCH);
		}
		else if (totalProfit <= 10000000)
		{
			//[100k,10m)
			totalProfitVal.setForeground(CustomColors.OFF_WHITE);
		}
		else
		{
			//[10m,inf)
			totalProfitVal.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
		}
	}

	/**
	 * Updates the hourly profit value display. Also checks and sets the font color according to profit/loss.
	 */
	private void updateHourlyProfitDisplay(Duration accumulatedTime)
	{
		String profitString;
		double divisor = accumulatedTime.toMillis() / 1000 * 1.0 / (60 * 60);

		if (divisor != 0)
		{
			profitString = UIUtilities.quantityToRSDecimalStack((long) (totalProfit / divisor), true);
		}
		else
		{
			profitString = "0";
		}

		hourlyProfitVal.setText(profitString + " gp/hr");
		hourlyProfitVal.setForeground(totalProfit >= 0 ? ColorScheme.GRAND_EXCHANGE_PRICE : CustomColors.OUTDATED_COLOR);
		hourlyProfitPanel.setToolTipText("Hourly profit as determined by the session time");
	}

	/**
	 * Updates the total ROI value display. Also checks and sets the font color according to profit/loss.
	 */
	private void updateRoiDisplay()
	{
		float roi = (float) totalProfit / totalExpenses * 100;

		if (totalExpenses == 0)
		{
			roiVal.setText("0.00%");
			roiVal.setForeground(CustomColors.TOMATO);
			return;
		}
		else
		{
			roiVal.setText(String.format("%.2f", (float) totalProfit / totalExpenses * 100) + "%");
		}

		roiVal.setForeground(UIUtilities.gradiatePercentage(roi, plugin.getConfig().roiGradientMax()));
		roiPanel.setToolTipText("<html>Return on investment:<br>Percentage of profit relative to gp invested</html>");
	}

	private void updateTotalFlipsDisplay()
	{
		totalFlipsVal.setText(QuantityFormatter.formatNumber(totalFlips));
		totalFlipsVal.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		totalFlipsPanel.setToolTipText("<html>Total amount of flips completed" +
			"<br>Does not count margin checks</html>");
	}

	private void updateMostCommonFlip()
	{
		if (mostCommonItemName == null || mostFlips == 0)
		{
			mostCommonFlipVal.setText("None");
			mostCommonFlipVal.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			return;
		}
		mostCommonFlipVal.setText(mostCommonItemName);
		mostCommonFlipVal.setToolTipText("Flipped " + mostFlips + (mostFlips == 1 ? " time" : " times"));
		mostCommonFlipVal.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		mostCommonFlipPanel.setToolTipText("<html>Most commonly flipped item determined by the item with most flips completed" +
			"<br>Does not count margin checks</html>");
	}

	/**
	 * This is called every second by the executor service in FlippingPlugin
	 */
	public void updateTimeDisplay()
	{
		activePanels.forEach(StatItemPanel::updateTimeLabels);
	}

	/**
	 * This is called by updateSessionTime in FlippingPlugin, which itself is called every second by
	 * the executor service.
	 *
	 * @param accumulatedTime The total time the user has spent flipping since the client started up.
	 */
	public void updateSessionTimeDisplay(Duration accumulatedTime)
	{
		sessionTimeVal.setText(TimeFormatters.formatDuration(accumulatedTime));
	}

	/**
	 * Invalidates a FlippingItems offers for the currently picked time interval.
	 * This means the panel will not be built upon the next rebuild calls of StatPanel for that time interval.
	 *
	 * @param itemPanel The panel which holds the FlippingItem to be terminated.
	 */
	public void deletePanel(StatItemPanel itemPanel)
	{
		if (!activePanels.contains(itemPanel))
		{
			return;
		}

		FlippingItem item = itemPanel.getFlippingItem();
		item.invalidateOffers(item.getIntervalHistory(startOfInterval));
		if (!plugin.getAccountCurrentlyViewed().equals(FlippingPlugin.ACCOUNT_WIDE)) {
			plugin.markAccountTradesAsHavingChanged(plugin.getAccountCurrentlyViewed());
		}
		rebuild(plugin.viewTradesForCurrentView());
	}

	/**
	 * Gets called every time the time interval combobox has its selection changed.
	 * Sets the start interval of the profit calculation.
	 *
	 * @param selectedInterval The string from TIME_INTERVAL_STRINGS that is selected in the time interval combobox
	 */
	public void setTimeInterval(String selectedInterval)
	{
		if (selectedInterval == null)
		{
			return;
		}

		Instant timeNow = Instant.now();

		if (selectedInterval.equals("Session")) {
			startOfInterval = plugin.viewStartOfSessionForCurrentView();
			startOfIntervalName = "Session";
		}
		else if (selectedInterval.equals("All")) {
			startOfInterval = Instant.EPOCH;
			startOfIntervalName = "All";
		}
		else {
			if (selectedInterval.length() < 3) {
				JOptionPane.showMessageDialog(timeIntervalDropdown, "Invalid input. Valid input is a negative whole number followed by an abbreviated unit of time. For example," +
						"-123h or -2d or -55w or -2m or -1y are valid inputs.", "Invalid Input",  JOptionPane.ERROR_MESSAGE);
				return;
			}

			String timeUnitString = String.valueOf(selectedInterval.charAt(selectedInterval.length() - 1));
			if (!TimeFormatters.stringToTimeUnit.containsKey(timeUnitString)) {
				JOptionPane.showMessageDialog(timeIntervalDropdown, "Invalid input. Valid input is a negative whole number followed by an abbreviated unit of time. For example," +
						"-123h or -2d or -55w or -2m or -1y are valid inputs.", "Invalid Input",  JOptionPane.ERROR_MESSAGE);
				return;
			}

			try {
				int amountToSubtract = Integer.parseInt(selectedInterval.substring(1, selectedInterval.length() - 1)) * (int) TimeFormatters.stringToTimeUnit.get(timeUnitString);
				startOfInterval = timeNow.minus(amountToSubtract, ChronoUnit.HOURS);
				startOfIntervalName = selectedInterval;

			} catch (NumberFormatException e) {
				JOptionPane.showMessageDialog(timeIntervalDropdown, "Invalid input. Valid input is a negative whole number followed by an abbreviated unit of time. For example," +
						"-123h or -2d or -55w or -2m or -1y are valid inputs.", "Invalid Input",  JOptionPane.ERROR_MESSAGE);
				return;
			}
		}
		paginator.setPageNumber(1);
		rebuild(plugin.viewTradesForCurrentView());
	}

	/**
	 * Chooses the font that is used for the sub information based on user config.
	 */
	private void updateSubInfoFont()
	{
		for (int i = 0; i < textLabelArray.length; i++)
		{
			textLabelArray[i].setFont(plugin.getFont());
			valLabelArray[i].setFont(plugin.getFont());
		}
	}

	/**
	 * Clones and sorts the to-be-built tradeList items according to the selectedSort string.
	 *
	 * @param tradeList The soon-to-be drawn tradeList whose items are getting sorted.
	 * @return Returns a cloned and sorted tradeList as specified by the selectedSort string.
	 */
	public List<FlippingItem> sortTradeList(List<FlippingItem> tradeList)
	{
		List<FlippingItem> result = new ArrayList<>(tradeList);

		if (selectedSort == null || result.isEmpty())
		{
			return result;
		}

		switch (selectedSort)
		{
			case "Most Recent":
				result.sort(Comparator.comparing(FlippingItem::getLatestActivityTime));
				break;

			case "Most Total Profit":
				result.sort((item1, item2) -> {
					ArrayList<OfferEvent> intervalHistory1 = item1.getIntervalHistory(startOfInterval);
					ArrayList<OfferEvent> intervalHistory2 = item2.getIntervalHistory(startOfInterval);

					long totalExpense1 = item1.getFlippedCashFlow(intervalHistory1, true);
					long totalRevenue1 = item1.getFlippedCashFlow(intervalHistory1, false);

					long totalExpense2 = item2.getFlippedCashFlow(intervalHistory2, true);
					long totalRevenue2 = item2.getFlippedCashFlow(intervalHistory2, false);

					if ((totalExpense1 != 0 && totalRevenue1 != 0) && (totalExpense2 == 0 || totalRevenue2 == 0))
					{
						return 1;
					}

					if ((totalExpense1 == 0 || totalRevenue1 == 0) && (totalExpense2 != 0 && totalRevenue2 != 0))
					{
						return -1;
					}

					if ((totalExpense1 == 0 || totalRevenue1 == 0) && (totalExpense2 == 0 || totalRevenue2 == 0))
					{
						return 0;
					}

					return Long.compare(item1.currentProfit(intervalHistory1), item2.currentProfit(intervalHistory2));
				});
				break;

			case "Most Profit Each":
				result.sort(Comparator.comparing(item ->
				{
					ArrayList<OfferEvent> intervalHistory = item.getIntervalHistory(startOfInterval);
					int quantity = item.countItemsFlipped(intervalHistory);

					if (quantity == 0)
					{
						return 0;
					}

					return (int) item.currentProfit(intervalHistory) / quantity;
				}));
				break;
			case "Highest ROI":
				result.sort((item1, item2) ->
				{
					ArrayList<OfferEvent> intervalHistory1 = item1.getIntervalHistory(startOfInterval);
					ArrayList<OfferEvent> intervalHistory2 = item2.getIntervalHistory(startOfInterval);

					long totalExpense1 = item1.getFlippedCashFlow(intervalHistory1, true);
					long totalRevenue1 = item1.getFlippedCashFlow(intervalHistory1, false);

					long totalExpense2 = item2.getFlippedCashFlow(intervalHistory2, true);
					long totalRevenue2 = item2.getFlippedCashFlow(intervalHistory2, false);

					if ((totalExpense1 != 0 && totalRevenue1 != 0) && (totalExpense2 == 0 || totalRevenue2 == 0))
					{
						return 1;
					}

					if ((totalExpense1 == 0 || totalRevenue1 == 0) && (totalExpense2 != 0 && totalRevenue2 != 0))
					{
						return -1;
					}

					if ((totalExpense1 == 0 || totalRevenue1 == 0) && (totalExpense2 == 0 || totalRevenue2 == 0))
					{
						return 0;
					}

					return Float.compare((float) item1.currentProfit(intervalHistory1) / totalExpense1, (float) item2.currentProfit(intervalHistory2) / totalExpense2);
				});
				break;

			case "Highest Quantity":
				result.sort(Comparator.comparing(item -> item.countItemsFlipped(item.getIntervalHistory(startOfInterval))));
				break;

			default:
				throw new IllegalStateException("Unexpected value: " + selectedSort);
		}
		Collections.reverse(result);

		return result;
	}

	private JLabel createResetButton() {
		JLabel resetIcon = new JLabel(Icons.TRASH_ICON_OFF);
		resetIcon.setPreferredSize(Icons.ICON_SIZE);
		resetIcon.setToolTipText("Reset Statistics");
		resetIcon.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					//Display warning message
					final int result = JOptionPane.showOptionDialog(resetIcon, "<html>Are you sure you want to reset the statistics?" +
									"<br>This only resets the statistics within the currently selected time interval</html>",
							"Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
							null, new String[]{"Yes", "No"}, "No");

					//If the user pressed "Yes"
					if (result == JOptionPane.YES_OPTION)
					{
						plugin.invalidateOffers(startOfInterval);
						rebuild(plugin.viewTradesForCurrentView());
					}
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				resetIcon.setIcon(Icons.TRASH_ICON);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				resetIcon.setIcon(Icons.TRASH_ICON_OFF);
			}
		});
		return resetIcon;
	}

	private JLabel createDownloadButton() {
		JPanel parent = this;
		JLabel downloadIcon = new JLabel(Icons.DONWLOAD_ICON_OFF);
		downloadIcon.setPreferredSize(Icons.ICON_SIZE);
		downloadIcon.setToolTipText("Export to CSV");
		downloadIcon.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				JFileChooser f = new JFileChooser();
				f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				f.showSaveDialog(parent);
				File selectedDirectory = f.getSelectedFile();
				if (selectedDirectory == null) {
					return;
				}
				log.info("exporting to csv in folder {}", f.getSelectedFile());
				try {
					plugin.exportToCsv(f.getSelectedFile(), startOfInterval, startOfIntervalName);
					JOptionPane.showMessageDialog(
							parent,
							String.format("Successfully saved csv file to %s/%s.csv", f.getSelectedFile().toString(), plugin.getAccountCurrentlyViewed()),
							"Successfully saved CSV!",
							JOptionPane.INFORMATION_MESSAGE
					);
				}
				catch (Exception exc) {
					JOptionPane.showMessageDialog(
							parent,
							String.format("Could not save CSV file. Error: %s", exc.toString()),
							"Could not save csv file",
							JOptionPane.ERROR_MESSAGE);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				downloadIcon.setIcon(Icons.DOWNLOAD_ICON);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				downloadIcon.setIcon(Icons.DONWLOAD_ICON_OFF);
			}
		});
		return downloadIcon;
	}

	private JPanel createTopPanel() {
		JPanel topPanel = new JPanel(new BorderLayout());
		IconTextField searchBar = UIUtilities.createSearchBar(this.executor, this::updateSearch);
		searchBar.setBorder(BorderFactory.createMatteBorder(1,1,1,1, ColorScheme.DARKER_GRAY_COLOR.darker()));

		JPanel searchAndDownloadPanel = new JPanel(new BorderLayout());
		searchAndDownloadPanel.add(searchBar, BorderLayout.CENTER);
		searchAndDownloadPanel.add(this.createDownloadButton(), BorderLayout.EAST);
		searchAndDownloadPanel.setBorder(new EmptyBorder(5,0,0,0));

		topPanel.add(searchAndDownloadPanel, BorderLayout.SOUTH);
		topPanel.add(timeIntervalDropdown, BorderLayout.CENTER);
		topPanel.add(this.createResetButton(), BorderLayout.EAST);
		topPanel.setBorder(new EmptyBorder(0,0,2,0));
		return topPanel;
	}

	private void prepareLabels() {
		Arrays.stream(textLabelArray).forEach(l -> l.setForeground(ColorScheme.GRAND_EXCHANGE_ALCH));

		//To ensure the item's name won't wrap the whole panel.
		mostCommonFlipVal.setMaximumSize(new Dimension(145, 0));

		sessionTimeVal.setText(TimeFormatters.formatDuration(plugin.viewAccumulatedTimeForCurrentView()));
		sessionTimeVal.setPreferredSize(new Dimension(200, 0));
		sessionTimeVal.setForeground(ColorScheme.GRAND_EXCHANGE_ALCH);

		//Profit total over the selected time interval
		totalProfitVal.setFont(BIG_PROFIT_FONT);
		totalProfitVal.setHorizontalAlignment(SwingConstants.CENTER);
		totalProfitVal.setToolTipText("");
	}

	private JComboBox createSortDropdown() {
		JComboBox sortDropdown = new JComboBox<>(SORT_BY_STRINGS);
		sortDropdown.setSelectedItem("Most Recent");
		sortDropdown.setRenderer(new ComboBoxListRenderer());
		sortDropdown.setEditable(true);
		sortDropdown.setBorder(BorderFactory.createMatteBorder(1,1,1,1, ColorScheme.DARKER_GRAY_COLOR.darker()));
		sortDropdown.setPreferredSize(new Dimension(sortDropdown.getWidth(), 32));
		sortDropdown.setFocusable(false);
		sortDropdown.setBackground(CustomColors.DARK_GRAY_LIGHTER);

		sortDropdown.addActionListener(event ->
		{
			selectedSort = (String) sortDropdown.getSelectedItem();

			if (selectedSort == null)
			{
				return;
			}
			rebuild(plugin.viewTradesForCurrentView());
		});
		return sortDropdown;
	}

	private JPanel createTotalProfitPanel(JPanel subInfoPanel) {
		//Title text for the big total profit label.
		final JLabel profitText = new JLabel("Total Profit: ", SwingConstants.CENTER);
		profitText.setForeground(ColorScheme.GRAND_EXCHANGE_ALCH);
		profitText.setFont(FontManager.getRunescapeBoldFont());

		JLabel arrowIcon = new JLabel(Icons.OPEN_ICON);
		arrowIcon.setPreferredSize(ICON_SIZE);

		//Make sure the profit label is centered
		JLabel padLabel = new JLabel();
		padLabel.setPreferredSize(ICON_SIZE);

		//Formats the profit text and value.
		JPanel profitTextAndVal = new JPanel(new BorderLayout());
		profitTextAndVal.setBackground(CustomColors.DARK_GRAY);
		profitTextAndVal.setBorder(new EmptyBorder(5,0,3,0));
		profitTextAndVal.add(totalProfitVal, BorderLayout.CENTER);
		profitTextAndVal.add(profitText, BorderLayout.NORTH);

		//Contains the total profit information.
		JPanel totalProfitPanel = new JPanel(new BorderLayout());
		totalProfitPanel.setBackground(CustomColors.DARK_GRAY);
		totalProfitPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,1,0,1, ColorScheme.DARKER_GRAY_COLOR.darker()),
				new EmptyBorder(7,0,7,0)));
		totalProfitPanel.add(profitTextAndVal, BorderLayout.CENTER);
		totalProfitPanel.add(arrowIcon, BorderLayout.EAST);
		totalProfitPanel.add(padLabel, BorderLayout.WEST);

		//Controls the collapsible sub info function
		MouseAdapter collapseOnClick = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					if (subInfoPanel.isVisible())
					{
						//Collapse sub info
						arrowIcon.setIcon(Icons.CLOSE_ICON);
						subInfoPanel.setVisible(false);
						totalProfitPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,1,1,1, ColorScheme.DARKER_GRAY_COLOR.darker()),
								new EmptyBorder(7,0,7,0)));
					}
					else
					{
						//Expand sub info
						arrowIcon.setIcon(Icons.OPEN_ICON);
						subInfoPanel.setVisible(true);
						totalProfitPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,1,0,1, ColorScheme.DARKER_GRAY_COLOR.darker()),
								new EmptyBorder(7,0,7,0)));
					}
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				totalProfitPanel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				profitTextAndVal.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				totalProfitPanel.setBackground(CustomColors.DARK_GRAY);
				profitTextAndVal.setBackground(CustomColors.DARK_GRAY);
			}
		};

		totalProfitPanel.addMouseListener(collapseOnClick);
		totalProfitVal.addMouseListener(collapseOnClick);

		return totalProfitPanel;
	}

	private JPanel createSubInfoPanel() {
		JPanel subInfoPanel = new JPanel();
		/* Subinfo represents the less-used general historical stats */
		subInfoPanel.setLayout(new DynamicGridLayout(subInfoPanelArray.length, 1));

		//All labels should already be sorted in their arrays.
		for (int i = 0; i < subInfoPanelArray.length; i++)
		{
			subInfoPanelArray[i].add(textLabelArray[i], BorderLayout.WEST);
			subInfoPanelArray[i].add(valLabelArray[i], BorderLayout.EAST);
		}

		subInfoPanel.setBackground(CustomColors.DARK_GRAY);
		subInfoPanel.setBorder(new EmptyBorder(9, 5, 5, 5));
		subInfoPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0,2,2,2, ColorScheme.DARKER_GRAY_COLOR.darker()),
				new EmptyBorder(2, 5, 5, 5)));
		return subInfoPanel;
	}

	private JPanel createSessionTimePanel() {
		JPanel sessionTimePanel = new JPanel(new BorderLayout());
		sessionTimePanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e))
				{
					//Display warning message
					final int result = JOptionPane.showOptionDialog(sessionTimePanel, "Are you sure you want to reset the session time?",
							"Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
							null, new String[]{"Yes", "No"}, "No");

					//If the user pressed "Yes"
					if (result == JOptionPane.YES_OPTION)
					{
						plugin.handleSessionTimeReset();
						rebuild(plugin.viewTradesForCurrentView());
					}
				}
			}
		});
		sessionTimePanel.setToolTipText("Right-click to reset session timer");
		return sessionTimePanel;
	}

	private JPanel createSortAndScrollContainer() {
		JPanel sortPanel = new JPanel(new BorderLayout());
		sortPanel.setBorder(new EmptyBorder(0, 0, 0, 90));
		sortPanel.add(sortDropdown, BorderLayout.CENTER);

		statItemPanelsContainer.setLayout(new BoxLayout(statItemPanelsContainer, BoxLayout.Y_AXIS));

		JPanel statItemPanelsContainerWrapper = new JPanel(new BorderLayout());
		statItemPanelsContainerWrapper.setBorder(new EmptyBorder(0,0,0,3));
		statItemPanelsContainerWrapper.add(statItemPanelsContainer, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(statItemPanelsContainerWrapper);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setBorder(new EmptyBorder(5, 0, 0, 0));
		scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(2, 0));

		//itemContainer holds the StatItems along with its sorting selector.
		JPanel sortAndScrollContainer = new JPanel(new BorderLayout());
		sortAndScrollContainer.setBorder(new EmptyBorder(15,0,5,0));
		sortAndScrollContainer.add(sortPanel, BorderLayout.NORTH);
		sortAndScrollContainer.add(scrollPane, BorderLayout.CENTER);

		return sortAndScrollContainer;
	}

	private JPanel createProfitAndSubInfoContainer() {
		subInfoPanel = this.createSubInfoPanel();
		JPanel profitAndSubInfoContainer = new JPanel(new BorderLayout());
		profitAndSubInfoContainer.add(this.createTotalProfitPanel(subInfoPanel), BorderLayout.NORTH);
		profitAndSubInfoContainer.add(subInfoPanel, BorderLayout.SOUTH);
		profitAndSubInfoContainer.setBorder(new EmptyBorder(5, 0, 5, 0));
		return profitAndSubInfoContainer;
	}

	private Paginator createPaginator() {
		paginator = new Paginator(() -> SwingUtilities.invokeLater(() -> {
			Instant rebuildStart = Instant.now();
			rebuildStatItemContainer(plugin.viewTradesForCurrentView());
			revalidate();
			repaint();
			log.info("page change took {}", Duration.between(rebuildStart, Instant.now()).toMillis());
		}));
		paginator.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		paginator.setBorder(new EmptyBorder(0, 0, 0, 10));
		return paginator;
	}
}