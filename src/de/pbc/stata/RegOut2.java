package de.pbc.stata;

import java.io.FileOutputStream;
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
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
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
 * More flexible improvement of RegOut.
 * </p>
 * <p>
 * Takes the following arguments:
 * <ul>
 * <li>{@code path}: path of output file (regOut.xlsx default)</li>
 * <li>{@code m/merge}: merge with existing Excel file, if it exist (in
 * combination with {@code copy} the existing file is used as input)</li>
 * </ul>
 * </p>
 */
public class RegOut2 {
	
	// VARIABLES ----------------------------------------------------- //
	
	private Path path;
	
	private boolean merge;
	
	private String cmd;
	
	private RegPar regPar;
	
	private XSSFWorkbook wb;
	
	// ENTRY POINT --------------------------------------------------- //
	
	public static int start(String[] args) throws Exception {
		return new RegOut2().execute(args);
	}
	
	// PUBLIC ------------------------------------------------------- //
	
	public int execute(String[] args) {
		if (Macro.getGlobal("cmd", Macro.TYPE_ERETURN) == null)
			throw new RuntimeException("no estimation stored");
		
		List<String> argsList = Arrays.asList(args).stream().map((s) -> s.toLowerCase()).collect(Collectors.toList());
		
		merge = argsList.contains("m") || argsList.contains("merge");
		cmd = Macro.getGlobal("cmd", Macro.TYPE_ERETURN);
		regPar = RegPars.byCmd(cmd);
		String[] termNames = Matrix.getMatrixColNames("e(b)");
		double[][] resultsTable = StataUtils.getMatrix("r(table)");
		Variable dv = new Variable(Macro.getGlobal("depvar", Macro.TYPE_ERETURN));
		
		path = argsList.stream()
				.filter((a) -> a.startsWith("path="))
				.findFirst()
				.map((a) -> Paths.get(a.substring("path=".length())))
				.orElse(Paths.get("regOut.xlsx"));
		
		try (XSSFWorkbook wb = merge && Files.exists(path)
				? new XSSFWorkbook(Files.newInputStream(path))
				: new XSSFWorkbook()) {
			this.wb = wb;
			
			wb.setMissingCellPolicy(Row.CREATE_NULL_AS_BLANK);
			XSSFSheet sh = Optional.ofNullable(wb.getSheet("Sheet0")).orElseGet(() -> wb.createSheet());
			wb.setActiveSheet(wb.getSheetIndex(sh));
			wb.setSelectedTab(wb.getSheetIndex(sh));
			
			if (cmd.equals("sqreg")) {
				SFIToolkit.executeCommand("local coleq : coleq e(b)", false);
				String[] equations = StataUtils.getMacroArray("coleq");
				for (String eq : Arrays.stream(equations).distinct().collect(Collectors.toList())) {
					List<Term> terms = new ArrayList<>();
					for (int col = 0; col < equations.length; col++) {
						if (equations[col].equals(eq)) {
							terms.add(new Term(col,
									termNames[col],
									resultsTable[0][col],
									resultsTable[1][col],
									resultsTable[3][col]));
						}
					}
					addModel(sh, terms, String.format("%s (%s)", dv, eq));
				}
			} else {
				List<Term> terms = new ArrayList<>(termNames.length);
				for (int i = 0; i < termNames.length; i++)
					terms.add(new Term(i, termNames[i], resultsTable[0][i], resultsTable[1][i], resultsTable[3][i]));
				addModel(sh, terms, dv.getLabel());
			}
			
			try (FileOutputStream out = new FileOutputStream(path.toFile())) {
				wb.write(out);
			}
			
			SFIToolkit.display("{browse \"" + path + "\":Open " + path + "}" + "\n");
			Macro.setGlobal("filename", "path=" + path.toString());
			
			return 0;
		} catch (Exception e) {
			SFIToolkit.error(SFIToolkit.stackTraceToString(e));
			return 45;
		}
	}
	
	// PRIVATE ------------------------------------------------------ //
	
	private void addModel(XSSFSheet sh, List<Term> terms, String modelTitle) {
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
		for (int col = 1; col < 101; col++) { // TODO no hard values
			c = r.getCell(col);
			if (c.getCellType() == Cell.CELL_TYPE_BLANK) {
				fillModel(sh, col, rows, terms, modelTitle);
				break;
			}
		}
	}
	
	private void fillModel(XSSFSheet sh, int col, Map<String, Integer> rows, List<Term> terms, String modelTitle) {
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
		
		c.setCellValue(modelTitle);
		
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
				} else if (!term.isBase()) {
					c.setCellValue(term.getCoefficient(2) + term.getSigStars() + " (" + term.getStandardError(2) + ")");
					c.setCellStyle(csText);
				}
			}
		}
		
		for (Term term : terms) {
			row++;
			r = sh.getRow(row) != null ? sh.getRow(row) : sh.createRow(row);
			
			c = r.getCell(0);
			if (c.getCellType() == Cell.CELL_TYPE_BLANK) {
				c.setCellValue(term.getLabel());
			} else {
				int tmpRow = row;
				rows.replaceAll((s, i) -> i >= tmpRow ? i + 1 : i);
				sh.shiftRows(row, sh.getLastRowNum(), 1);
				r = sh.createRow(row);
				c = r.getCell(0);
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
	
}