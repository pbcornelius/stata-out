package de.pbc.stata;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.stata.sfi.Data;
import com.stata.sfi.Macro;
import com.stata.sfi.Matrix;
import com.stata.sfi.SFIToolkit;
import com.stata.sfi.Scalar;

/**
 * <p>
 * Stata Java plugin to write Stata outputs in Excel.
 * </p>
 * <p>
 * Takes the following arguments:
 * <ul>
 * <li>{@code e/excel}: output in Excel file (required)</li>
 * <li>{@code path}: path of output file (regOut.xlsx default)</li>
 * <li>{@code c/copy}: create a copy of the output file, if the output file
 * exists already</li>
 * <li>{@code m/merge}: merge with existing Excel file, if it exist (in
 * combination with {@code copy} the existing file is used as input)</li>
 * <li>{@code s/singlepage}: single page output (multipage default)</li>
 * <li>{@code q/quietly}: print file link to Stata console</li>
 * </ul>
 * </p>
 * 
 * @author Philipp Cornelius
 * @version 2 (2018-09-28)
 */
public class RegOut {
	
	// VARIABLES ----------------------------------------------------- //
	
	private Path path;
	
	private boolean merge, singlePage, quietly;
	
	private String cmd;
	
	private RegPar regPar;
	
	private List<Term> terms;
	
	private double[][] table;
	
	private XSSFWorkbook wb;
	
	// ENTRY POINT --------------------------------------------------- //
	
	public static int start(String[] args) throws Exception {
		return new RegOut().execute(args);
	}
	
	// PUBLIC ------------------------------------------------------- //
	
	public int execute(String[] args) {
		if (Macro.getGlobal("cmd", Macro.TYPE_ERETURN) == null)
			throw new RuntimeException("no estimation stored");
		
		List<String> argsList = Arrays.asList(args).stream().map((s) -> s.toLowerCase()).collect(Collectors.toList());
		
		cmd = Macro.getGlobal("cmd", Macro.TYPE_ERETURN);
		regPar = RegPars.byCmd(cmd);
		
		if (argsList.contains("e") || argsList.contains("excel"))
			return excelOut(argsList);
		else
			throw new RuntimeException("no output specified");
	}
	
	// PRIVATE ------------------------------------------------------ //
	
	private int excelOut(List<String> args) {
		merge = args.contains("m") || args.contains("merge");
		singlePage = args.contains("s") || args.contains("singlepage");
		quietly = args.contains("q") || args.contains("quietly");
		
		String[] termNames = Matrix.getMatrixColNames("e(b)");
		table = StataUtils.getMatrix("r(table)");
		terms = new ArrayList<>(termNames.length);
		for (int i = 0; i < termNames.length; i++)
			terms.add(new Term(i, termNames[i], table[0][i], table[1][i], table[3][i]));
		
		path = args.stream()
				.filter((a) -> a.startsWith("path="))
				.findFirst()
				.map((a) -> Paths.get(a.substring("path=".length())))
				.orElse(Paths.get("regOut.xlsx"));
		
		try (XSSFWorkbook wb = merge && Files.exists(path)
				? new XSSFWorkbook(Files.newInputStream(path))
				: new XSSFWorkbook()) {
			this.wb = wb;
			
			if (singlePage)
				singlePage();
			else
				multiPage();
			
			try (FileOutputStream out = getOutputStream(path)) {
				wb.write(out);
			}
			
			if (!quietly)
				SFIToolkit.display("{browse \"" + path + "\":Open " + path + "}" + "\n");
			Macro.setGlobal("filename", "path=" + path.toString());
			
			return 0;
		} catch (Exception e) {
			SFIToolkit.error(SFIToolkit.stackTraceToString(e));
			return 45;
		}
	}
	
	private String iterateSheetName(String name, int i) {
		String tmpName = i < 1 ? name : name + i;
		if (wb.getSheet(tmpName) == null)
			return tmpName;
		else
			return iterateSheetName(name, i + 1);
	}
	
	private void multiPage() {
		Function<Double, String> sigLevels = (p) -> p < .01 ? "***" : p < .05 ? "**" : p < .1 ? "*" : "";
		
		XSSFSheet sh = wb.createSheet(iterateSheetName(WorkbookUtil.createSafeSheetName(Macro.getGlobal("depvar",
				Macro.TYPE_ERETURN).replaceAll("[\\s\\v]+", " ")), 0));
		
		wb.setActiveSheet(wb.getSheetIndex(sh));
		wb.setSelectedTab(wb.getSheetIndex(sh));
		
		Row r = sh.createRow(0);
		r.createCell(0).setCellValue(Macro.getGlobal("depvar", Macro.TYPE_ERETURN).replaceAll("[\\s\\v]+", " "));
		r.createCell(2).setCellValue("Coef.");
		r.createCell(3).setCellValue("Std. Err.");
		r.createCell(4).setCellValue("t/z");
		r.createCell(5).setCellValue("P-value");
		r.createCell(6).setCellValue("[95%");
		r.createCell(7).setCellValue("95%]");
		
		CellStyle cs0d = wb.createCellStyle();
		cs0d.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
		
		CellStyle cs2d = wb.createCellStyle();
		cs2d.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
		
		CellStyle cs3d = wb.createCellStyle();
		cs3d.setDataFormat(wb.createDataFormat().getFormat("0.000"));
		
		XSSFCellStyle csText = wb.createCellStyle();
		csText.setAlignment(HorizontalAlignment.RIGHT);
		
		for (int i = 0; i < terms.size(); i++) {
			r = sh.createRow(i + 1);
			if (terms.get(i).isBase()) {
				r.createCell(0).setCellValue("base " + terms.get(i).getLabel());
			} else {
				r.createCell(0).setCellValue(terms.get(i).getLabel());
				if (!terms.get(i).isOmitted()) {
					r.createCell(1)
							.setCellValue(BigDecimal.valueOf(table[0][i]).setScale(2, RoundingMode.HALF_UP) + sigLevels
									.apply(table[3][i]));
					r.getCell(1).setCellStyle(csText);
					r.createCell(2).setCellValue(table[0][i]);
					r.getCell(2).setCellStyle(cs2d);
					r.createCell(3).setCellValue(table[1][i]);
					r.getCell(3).setCellStyle(cs2d);
					r.createCell(4).setCellValue(table[2][i]);
					r.getCell(4).setCellStyle(cs2d);
					r.createCell(5).setCellValue(table[3][i]);
					r.getCell(5).setCellStyle(cs3d);
					r.createCell(6).setCellValue(table[4][i]);
					r.getCell(6).setCellStyle(cs2d);
					r.createCell(7).setCellValue(table[5][i]);
					r.getCell(7).setCellStyle(cs2d);
				} else {
					r.createCell(1).setCellValue("0 (omitted)");
					r.getCell(1).setCellStyle(csText);
				}
			}
		}
		
		r = sh.createRow(terms.size() + 1);
		r.createCell(0).setCellValue("N");
		r.createCell(1).setCellValue(Scalar.getValue("N", Scalar.TYPE_ERETURN));
		r.getCell(1).setCellStyle(cs0d);
		
		if (regPar.hasGroups()) {
			r = sh.createRow(terms.size() + 2);
			r.createCell(0).setCellValue("Groups");
			r.createCell(1).setCellValue(Scalar.getValue("N_g", Scalar.TYPE_ERETURN));
			r.getCell(1).setCellStyle(cs0d);
		}
		
		r = sh.createRow(terms.size() + 3);
		r.createCell(0).setCellValue(regPar.getStatName());
		r.createCell(1).setCellValue(Scalar.getValue(regPar.getStatId(), Scalar.TYPE_ERETURN));
		r.getCell(1).setCellStyle(cs2d);
		
		r = sh.createRow(terms.size() + 4);
		r.createCell(0).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		
		sh.autoSizeColumn(0);
		sh.autoSizeColumn(1);
		sh.autoSizeColumn(2);
		sh.autoSizeColumn(3);
		sh.autoSizeColumn(4);
		sh.autoSizeColumn(5);
		sh.autoSizeColumn(6);
		sh.autoSizeColumn(7);
	}
	
	private void singlePage() {
		wb.setMissingCellPolicy(Row.CREATE_NULL_AS_BLANK);
		
		XSSFSheet sh = Optional.ofNullable(wb.getSheet("Sheet0")).orElseGet(() -> wb.createSheet());
		
		wb.setActiveSheet(wb.getSheetIndex(sh));
		wb.setSelectedTab(wb.getSheetIndex(sh));
		
		XSSFRow r = Optional.ofNullable(sh.getRow(0)).orElseGet(() -> sh.createRow(0));
		
		XSSFCell c = r.getCell(0);
		if (c.getCellType() == Cell.CELL_TYPE_BLANK)
			c.setCellValue("Variables");
		
		Map<String, Integer> rows = new HashMap<>();
		for (int row = 1; row <= sh.getLastRowNum(); row++) {
			r = sh.getRow(row);
			c = r.getCell(0);
			if (c.getCellType() != Cell.CELL_TYPE_BLANK)
				rows.put(c.getStringCellValue(), row);
		}
		
		r = sh.getRow(0);
		for (int col = 1; col < 21; col++) { // TODO no hard values
			c = r.getCell(col);
			if (c.getCellType() == Cell.CELL_TYPE_BLANK) {
				singlePageFill(sh, col, rows);
				break;
			}
		}
	}
	
	private void singlePageFill(XSSFSheet sh, int col, Map<String, Integer> rows) {
		CellStyle cs0d = wb.createCellStyle();
		cs0d.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
		
		CellStyle cs2d = wb.createCellStyle();
		cs2d.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
		
		CellStyle cs3d = wb.createCellStyle();
		cs3d.setDataFormat(wb.createDataFormat().getFormat("0.000"));
		
		XSSFCellStyle csText = wb.createCellStyle();
		csText.setAlignment(HorizontalAlignment.RIGHT);
		
		int row = 0;
		XSSFRow r = sh.getRow(row);
		XSSFCell c = r.getCell(col);
		
		Variable dv = new Variable(Macro.getGlobal("depvar", Macro.TYPE_ERETURN));
		c.setCellValue(dv.getLabel());
		
		Iterator<Term> termsItr = terms.iterator();
		while (termsItr.hasNext()) {
			Term term = termsItr.next();
			
			if (rows.containsKey(term.getLabel())) {
				termsItr.remove();
				
				int termRow = rows.get(term.getLabel());
				if (!term.getName().equals("_cons"))
					row = Math.max(termRow, row);
				r = sh.getRow(termRow);
				
				c = r.getCell(col);
				if (term.isOmitted()) {
					c.setCellValue("0 (omitted)");
					c.setCellStyle(csText);
				} else {
					c.setCellValue(term.getCoefficient(2) + term.getSigStars() + " (" + term.getStandardError(2) + ")");
					c.setCellStyle(csText);
				}
			} else if (term.isBase() && rows.containsKey("base " + term.getLabel())) {
				termsItr.remove();
			}
		}
		
		for (Term term : terms) {
			row++;
			r = sh.getRow(row) != null ? sh.getRow(row) : sh.createRow(row);
			
			c = r.getCell(0);
			if (c.getCellType() == Cell.CELL_TYPE_BLANK) {
				if (term.isBase())
					c.setCellValue("base " + term.getLabel());
				else
					c.setCellValue(term.getLabel());
			} else {
				int tmpRow = row;
				rows.replaceAll((s, i) -> i >= tmpRow ? i + 1 : i);
				sh.shiftRows(row, sh.getLastRowNum(), 1);
				r = sh.createRow(row);
				c = r.getCell(0);
				if (term.isBase())
					c.setCellValue("base " + term.getLabel());
				else
					c.setCellValue(term.getLabel());
			}
			
			if (term.isOmitted()) {
				c = r.getCell(col);
				c.setCellValue("0 (omitted)");
				c.setCellStyle(csText);
			} else if (!term.isBase()) {
				c = r.getCell(col);
				c.setCellValue(term.getCoefficient(2) + term.getSigStars() + " (" + term.getStandardError(2) + ")");
				c.setCellStyle(csText);
			}
		}
		
		row = sh.getLastRowNum();
		int tmpRow;
		
		tmpRow = rows.containsKey(regPar.getStatName()) ? rows.get(regPar.getStatName()) : ++row;
		r = sh.getRow(tmpRow) != null ? sh.getRow(tmpRow) : sh.createRow(tmpRow);
		c = r.getCell(0);
		if (c.getCellType() == Cell.CELL_TYPE_BLANK)
			c.setCellValue(regPar.getStatName());
		c = r.getCell(col);
		Double testStat = Scalar.getValue(regPar.getStatId(), Scalar.TYPE_ERETURN);
		if (testStat != null && !Data.isValueMissing(testStat)) {
			c.setCellValue(testStat);
			c.setCellStyle(cs2d);
		}
		
		tmpRow = rows.containsKey("R²") ? rows.get("R²") : ++row;
		r = sh.getRow(tmpRow) != null ? sh.getRow(tmpRow) : sh.createRow(tmpRow);
		c = r.getCell(0);
		if (c.getCellType() == Cell.CELL_TYPE_BLANK)
			c.setCellValue("R²");
		c = r.getCell(col);
		
		Double r2Val;
		if (cmd.equals("logit")) {
			r2Val = Scalar.getValue("r2_p", Scalar.TYPE_ERETURN);
		} else if (cmd.equals("xtregar")) {
			r2Val = Scalar.getValue("r2_w", Scalar.TYPE_ERETURN);
		} else {
			r2Val = Scalar.getValue("r2", Scalar.TYPE_ERETURN);
		}
		
		// this number (instead of null) is returned by Stata if the value isn't present
		if (r2Val != null && !r2Val.equals(Math.pow(2, 1023))) {
			c.setCellValue(r2Val);
		}
		c.setCellStyle(cs2d);
		
		tmpRow = rows.containsKey("N") ? rows.get("N") : ++row;
		r = sh.getRow(tmpRow) != null ? sh.getRow(tmpRow) : sh.createRow(tmpRow);
		c = r.getCell(0);
		if (c.getCellType() == Cell.CELL_TYPE_BLANK)
			c.setCellValue("N");
		c = r.getCell(col);
		c.setCellValue(Scalar.getValue("N", Scalar.TYPE_ERETURN));
		c.setCellStyle(cs0d);
		
		if (regPar.hasGroups()) {
			tmpRow = rows.containsKey("Groups") ? rows.get("Groups") : ++row;
			r = sh.getRow(tmpRow) != null ? sh.getRow(tmpRow) : sh.createRow(tmpRow);
			c = r.getCell(0);
			if (c.getCellType() == Cell.CELL_TYPE_BLANK)
				c.setCellValue("Groups");
			c = r.getCell(col);
			c.setCellValue(Scalar.getValue("N_g", Scalar.TYPE_ERETURN));
			c.setCellStyle(cs0d);
		}
		
		tmpRow = rows.containsKey("created") ? rows.get("created") : ++row;
		r = sh.getRow(tmpRow) != null ? sh.getRow(tmpRow) : sh.createRow(tmpRow);
		c = r.getCell(0);
		if (c.getCellType() == Cell.CELL_TYPE_BLANK)
			c.setCellValue("created");
		c = r.getCell(col);
		c.setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		
		sh.autoSizeColumn(0);
		sh.autoSizeColumn(col);
	}
	
	private FileOutputStream getOutputStream(Path path) throws FileNotFoundException {
		return new FileOutputStream(path.toFile());
	}
	
}