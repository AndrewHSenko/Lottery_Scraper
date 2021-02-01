import java.util.*;
import java.util.stream.Collectors;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
/**
 * Scrapes the CA Daily Three lottery website and archived pages in the Wayback Machine
 * from Internet Archive to collect past winning combinations and prizes.
 * 
 *
 * @author Andrew Senkowski
 * @version 1/27/21
 */
public class HistoricalData
{
    ArrayList<String> hd;
    ArrayList<Data> classifiedHD;
    /**
     * Constructor for objects of class HistoricalData
     */
    public HistoricalData(int numOfCombos, boolean produceOldCombos, boolean produceExcelSheet)
    {
	hd = new ArrayList();
	classifiedHD = new ArrayList();
	if (updateData(numOfCombos, produceExcelSheet))
	    System.out.println("Successfully retrieved combos from current site.");
	else
	    System.err.println("Failed to retrieve combos from current site.");
	if (produceOldCombos) {
	    if (parseOldCombos)
		System.out.println("Successfully parsed DownloadAllNumbers.txt.");
	    else
		System.err.println("Failed to parse DownloadAllNumbers.txt.");
	}
    }
    /**
     *  Using file input line-by-line technique
     */
    public boolean parseOldCombos()
    {
	try
	{
	    FileInputStream fileInput = new FileInputStream("DownloadAllNumbers.txt");
	    Scanner sc = new Scanner(fileInput);
	    for (int i = 0; i < 5; i++) { // To bypass the initial informational lines
		sc.nextLine();
	    }
	    String formattedData = "";
	    while (sc.hasNextLine()) {
		String tempData = sc.nextLine();
		String[] tempDataSplit = tempData.split(" ");
		formattedData += tempDataSplit[0] + "," + tempDataSplit[18] + tempDataSplit[28] + tempDataSplit[38] + ","; // The corresponding indices for the relevant info
	    }
	    // Will need to find and access prize data
	    Path fileOutput = Path.of("OldLotteryCombosParsed.txt");
	    Files.writeString(fileOutput, formattedData);
	    return true;
	}
	catch (IOException ioe) {
	    System.err.println("IO Exception when updating data: " + ioe);
	    return false;
	}
    }
    /**
     * An example of a method - replace this comment with your own
     * Using file conversion to string technique
     *
     * @param  y  a sample parameter for a method
     * @return    the sum of x and y
     */
    private boolean updateData(int numNewEntries, boolean makeSheet)
    {
	int i = 1;
	int numEntries = numNewEntries;
	while (numEntries > 20) { // Gets us to the initial starting page to begin our scraping
	    numEntries -= 20;
	    i++;
	}
	while (i != 0) {
	    String url = "https://www.calottery.com/api/DrawGameApi/DrawGamePastDrawResults/9/" + i + "/20";
	    // Could be optimized further using various other libraries like AsyncHttpClient
	    try {
		BufferedInputStream in = new BufferedInputStream(new URL(url).openStream());
		String formattedData = interpretData(in, true, numEntries);
		if (makeSheet)
			makeDatasheet(formattedData, "NewHD");
		Path fileName = Path.of("NewHistoricalDataFullA.txt");
		String existingHD = Files.readString(fileName);
		formattedData += existingHD;
		fileName = Path.of("UpdatedHDCSV.txt");
		Files.writeString(fileName, formattedData);
		fileName = Path.of("NewHistoricalDataFullA.txt");
		BufferedWriter bw = new BufferedWriter(new FileWriter("NewHistoricalDataFullA.txt", false));
		bw.write(formattedData);
		bw.close();
		i--;
		numEntries = 20;
	    }
	    catch (MalformedURLException mfe) {
		System.err.println("Invalid URL received when updating data.");
		return false;
	    }
	    catch (IOException ioe) {
		for (int i = 0; i < 5; i++) { // To bypass the initial informational lines
		    sc.nextLine();
		}
		String formattedData = "";
		while (sc.hasNextLine()) {
		    String tempData = sc.nextLine();
		    String[] tempDataSplit = tempData.split(" ");
		    formattedData += tempDataSplit[0] + "," + tempDataSplit[18] + tempDataSplit[28] + tempDataSplit[38] + ","; // The corresponding indices for the relevant info
		}
		// Will need to find and access prize data
		Path fileOutput = Path.of("OldFormattedHD");
		Files.writeString(fileOutput, formattedData);
		return true;
	    }
	    catch (IOException ioe) {
		System.err.println("IO Exception when updating data: " + ioe);
		return false;
	    }
	}
	/**
	 * An example of a method - replace this comment with your own
	 *
	 * Could do with some optimization on the character-by-character parsing
	 * 
	 * @param  y  a sample parameter for a method
	 * @return    the sum of x and y
	 */
	private String interpretData(BufferedInputStream in, Boolean update, int numNewEntries)
	{
	    String rawRecord = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
	    String drawNumber = "";
	    String number = "";
	    String prizeType = "";
	    String prize = "";
	    String formattedEntry = "";
	    int i = 1000;
	    while (!rawRecord.substring(i, i + 13).equals("PreviousDraws")) {
		i++;
	    }
	    for (int a = 0; i < rawRecord.length() && (!update || a < numNewEntries); i++, a++) {
		try {
		    while (!rawRecord.substring(i, i + 10).equals("DrawNumber")) { // To get to the next "{" after which the entry begins
			i++;
		    }
		    i += 12;
		    while (!rawRecord.substring(i, i + 1).equals(",")) { // Generates drawNumber
			drawNumber += rawRecord.substring(i, i + 1); // Technically could hard-code distance, but wants to reduce undefined behavior when possible
			i++;
		    }
		    formattedEntry += drawNumber + ",";
		    while (!rawRecord.substring(i, i + 7).equals("\"Number")) { // To get to the numbers differentiated from "WinningNumbers"
			i++;
		    }
		    for (int j = 0; j < 3; j++) { // Generates number
			while (!rawRecord.substring(i, i + 3).equals("\":\"")) {
			    i++;
			}
			number += rawRecord.charAt(i + 3);
			i += 3;
		    }
		    formattedEntry += number + ",";
		    hd.add(number);
		    while (!rawRecord.substring(i, i + 9).equals("PrizeType")) { // To get to prizes
			i++;
		    }
		    for (int j = 0; j < 4; j++) {
			while (!rawRecord.substring(i, i + 3).equals("\":\"")) { // To get to the prize name
			    i++;
			}
			i += 2;
			while (!rawRecord.substring(i, i + 1).equals(",")) { // Generates prizeType
			    prizeType += rawRecord.substring(i, i + 1);
			    i++;
			}
			formattedEntry += prizeType.substring(1, prizeType.length() - 1) + ",";
			while (!rawRecord.substring(i, i + 6).equals("Amount")) { // To get to the prize itself
			    i++;
			}
			i += 8;
			while (!rawRecord.substring(i, i + 1).equals("}")) { // Generates prize
			    prize += rawRecord.substring(i, i + 1);
			    i++;
			}
			if (prizeType.equals("\"Box\"") && prize.length() == 2) // To fix formatting for a double-digit prize for Box only
			    formattedEntry += prize + ",";
			else
			    formattedEntry += prize + ",";
			prizeType = "";
			prize = "";
		    }
		    drawNumber = "";
		    number = "";
		}
		catch (StringIndexOutOfBoundsException end) {
		    break;
		}
	    }
	    return formattedEntry;
	}
	/**
	 * 
	 */
	private boolean makeDatasheet(String data, String fileName)
	{
	    try
	    {
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFSheet sheet = workbook.createSheet(fileName);
		XSSFRow row;
		Map<String, Object[]> entries = new TreeMap<String, Object[]>();
		Object[] prop = new Object[10];
		for (int i = 0, j = 0, k = 1;; j++) {
		    if (j == 10) {
			j = 0;
			entries.put(Integer.toString(k), prop);
			prop = new Object[10];
			k++;
		    }
		    if (i == data.length())
			break;
		    String entry_props = "";
		    while (!data.substring(i, i + 1).equals(",")) {
			entry_props += data.substring(i, i + 1);
			i++;
		    }
		    prop[j] = entry_props;
		    i++;
		}
		Set<String> keyid = entries.keySet();
		int rowid = 0;
		for (String key : keyid) {
		    row = sheet.createRow(rowid++);
		    Object[] objectArr = entries.get(key);
		    int cellid = 0;
		    for (Object obj : objectArr) {
			Cell cell = row.createCell(cellid++);
			cell.setCellValue((String)obj);
		    }
		}
		for (int i = 0; i < 10; i++)
		    sheet.autoSizeColumn(i);
		FileOutputStream out = new FileOutputStream(new File(fileName + ".xlsx"));
		workbook.write(out);
		out.close();
		return true;
	    }
	    catch (Exception e) {
		System.err.println("Issue making datasheet of HD");
		return false;
	    }
    }
}
