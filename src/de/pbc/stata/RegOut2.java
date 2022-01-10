package de.pbc.stata;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.stata.sfi.Macro;
import com.stata.sfi.SFIToolkit;

/**
 * <p>
 * Stata Java plugin to write Stata outputs in Excel.
 * More flexible improvement of RegOut.
 * </p>
 * <p>
 * Takes the following arguments:
 * <ul>
 * <li>{@code path=}: path of output file (regOut.xlsx default)</li>
 * <li>{@code m/merge}: merge with existing Excel file, if it exist</li>
 * <li>{@code sh/sheet[=]}: new sheet / sheet name
 * </ul>
 * </p>
 */
public class RegOut2 {
	
	// VARIABLES ----------------------------------------------------- //
	
	private String cmd;
	
	private ModelResult regPar;
	
	private XSSFWorkbook wb;
	
	private boolean hideOmitted, hideBase;
	
	// ENTRY POINT --------------------------------------------------- //
	
	public static int start(String[] args) throws Exception {
		return new RegOut2().execute(args);
	}
	
	// PUBLIC ------------------------------------------------------- //
	
	public int execute(String[] args) {
		if (Macro.getGlobal("cmd", Macro.TYPE_ERETURN) == null)
			throw new RuntimeException("no estimation stored");
		
		// otherwise the Excel file sometimes triggers an error: "Zip bomb detected!"
		ZipSecureFile.setMinInflateRatio(.0001);
		
		List<String> argsList = Arrays.asList(args).stream().map((s) -> s.toLowerCase()).collect(Collectors.toList());
		
		boolean merge = argsList.contains("m") || argsList.contains("merge");
		hideOmitted = argsList.contains("hideomitted");
		hideBase = argsList.contains("hidebase");
		cmd = Macro.getGlobal("cmd", Macro.TYPE_ERETURN);
		regPar = Models.byCmd(cmd);
		
		Path path = argsList.stream()
				.filter((a) -> a.startsWith("path="))
				.findFirst()
				.map((a) -> Paths.get(a.substring("path=".length())))
				.orElse(Paths.get("regOut.xlsx"));
		
		String sheet = argsList.stream()
				.filter(a -> a.startsWith("sheet") || a.startsWith("sh"))
				.findFirst()
				.map(s -> s.substring(s.indexOf("=") + 1))
				.orElse(null);
		
		try (XSSFWorkbook wb = merge && Files.exists(path)
				? new XSSFWorkbook(Files.newInputStream(path))
				: new XSSFWorkbook()) {
			this.wb = wb;
			
			wb.setMissingCellPolicy(Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
			
			XSSFSheet sh;
			if (Objects.isNull(sheet)) {
				sh = Optional.ofNullable(wb.getSheet("Sheet0")).orElseGet(() -> wb.createSheet());
			} else if (sheet.equals("sh") || sheet.equals("sheet")) {
				sh = wb.createSheet(iterateSheetName("Sheet", 1));
			} else {
				sh = Optional.ofNullable(wb.getSheet(WorkbookUtil.createSafeSheetName(sheet)))
						.orElseGet(() -> wb.createSheet(WorkbookUtil.createSafeSheetName(sheet)));
			}
			wb.setActiveSheet(wb.getSheetIndex(sh));
			wb.setSelectedTab(wb.getSheetIndex(sh));
			
			if (regPar.hasMultipleEquations()) {
				for (String eq : regPar.getEquations()) {
					addModel(sh, regPar.getTerms(eq), String.format("%s (%s)", regPar.getDv(), eq), eq);
				}
			} else {
				addModel(sh, regPar.getTerms(), regPar.getDv().getLabel(), null);
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
	
	private String iterateSheetName(String name, int i) {
		String tmpName = name + i;
		if (wb.getSheet(tmpName) == null)
			return tmpName;
		else
			return iterateSheetName(name, i + 1);
	}
	
	private void addModel(XSSFSheet sh, List<Term> terms, String modelTitle, String eq) {
		XSSFRow r = Optional.ofNullable(sh.getRow(0)).orElseGet(() -> sh.createRow(0));
		
		XSSFCell c = r.getCell(0);
		if (c.getCellType().equals(CellType.BLANK))
			c.setCellValue("Variables");
		
		Map<String, Integer> rows = new HashMap<>();
		for (int row = 1; row <= sh.getLastRowNum(); row++) {
			r = sh.getRow(row);
			c = r.getCell(0);
			if (!c.getCellType().equals(CellType.BLANK))
				rows.put(c.getStringCellValue(), row);
		}
		
		r = sh.getRow(0);
		for (int col = 1; col < 1001; col++) {
			c = r.getCell(col);
			if (c.getCellType().equals(CellType.BLANK)) {
				fillModel(sh, col, rows, terms, modelTitle, eq);
				break;
			}
		}
	}
	
	private void fillModel(XSSFSheet sh, int col, Map<String, Integer> rows, List<Term> terms, String modelTitle,
			String eq) {
		CellStyle cs0d = wb.createCellStyle();
		cs0d.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
		
		CellStyle cs2d = wb.createCellStyle();
		cs2d.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
		
		CellStyle cs3d = wb.createCellStyle();
		cs3d.setDataFormat(wb.createDataFormat().getFormat("0.000"));
		
		XSSFCellStyle csText = wb.createCellStyle();
		csText.setAlignment(HorizontalAlignment.RIGHT);
		
		XSSFRow r = sh.getRow(0);
		XSSFCell c = r.getCell(col);
		c.setCellValue(modelTitle);
		
		Name lastVarName = wb.getName(String.format("%s_lastvar", sh.getSheetName().replace('-', '_')));
		int row;
		if (Objects.nonNull(lastVarName)) {
			CellReference lastVarCR = new CellReference(lastVarName.getRefersToFormula());
			row = lastVarCR.getRow();
		} else {
			row = 0;
		}
		
		Iterator<Term> termsItr = terms.iterator();
		while (termsItr.hasNext()) {
			Term term = termsItr.next();
			
			if (!term.isConstant()) {
				termsItr.remove();
				
				if ((hideOmitted && term.isOmitted()) || (hideBase && term.isBase())) {
					continue;
				}
				
				if (rows.containsKey(term.getLabel())) {
					r = sh.getRow(rows.get(term.getLabel()));
					c = r.getCell(col);
				} else {
					row++;
					r = sh.getRow(row) != null ? sh.getRow(row) : sh.createRow(row);
					
					c = r.getCell(0);
					if (c.getCellType().equals(CellType.BLANK)) {
						c.setCellValue(term.getLabel());
					} else {
						int tmpRow = row;
						rows.replaceAll((s, i) -> i >= tmpRow ? i + 1 : i);
						sh.shiftRows(row, sh.getLastRowNum(), 1);
						r = sh.createRow(row);
						c = r.getCell(0);
						c.setCellValue(term.getLabel());
					}
				}
				
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
		
		if (Objects.nonNull(lastVarName)) {
			CellReference lastVarCR = new CellReference(lastVarName.getRefersToFormula());
			lastVarName.setRefersToFormula(String.format("%s",
					new CellReference(sh.getSheetName(), Math.max(row, lastVarCR.getRow()), 0, true, true)
							.formatAsString()));
		} else {
			lastVarName = wb.createName();
			lastVarName.setNameName(String.format("%s_lastvar", sh.getSheetName().replace('-', '_')));
			lastVarName.setRefersToFormula(String.format("%s",
					new CellReference(sh.getSheetName(), row, 0, true, true).formatAsString()));
		}
		
		// constant
		for (Term term : terms) {
			if (rows.containsKey(term.getLabel())) {
				r = sh.getRow(rows.get(term.getLabel()));
				c = r.getCell(col);
				c.setCellValue(term.getCoefficient(2) + term.getSigStars() + " (" + term.getStandardError(2) + ")");
				c.setCellStyle(csText);
			} else {
				row++;
				r = sh.getRow(row) != null ? sh.getRow(row) : sh.createRow(row);
				
				c = r.getCell(0);
				if (c.getCellType().equals(CellType.BLANK)) {
					c.setCellValue(term.getLabel());
				} else {
					int tmpRow = row;
					rows.replaceAll((s, i) -> i >= tmpRow ? i + 1 : i);
					sh.shiftRows(row, sh.getLastRowNum(), 1);
					r = sh.createRow(row);
					c = r.getCell(0);
					c.setCellValue(term.getLabel());
				}
				
				c = r.getCell(col);
				c.setCellValue(term.getCoefficient(2) + term.getSigStars() + " (" + term.getStandardError(2) + ")");
				c.setCellStyle(csText);
			}
		}
		
		row = sh.getLastRowNum();
		int tmpRow;
		
		if (Objects.nonNull(eq)) {
			for (ModelStat stat : regPar.getEquationStats(eq)) {
				tmpRow = rows.containsKey(stat.getLabel()) ? rows.get(stat.getLabel()) : ++row;
				r = sh.getRow(tmpRow) != null ? sh.getRow(tmpRow) : sh.createRow(tmpRow);
				c = r.getCell(0);
				if (c.getCellType().equals(CellType.BLANK))
					c.setCellValue(stat.getLabel());
				c = r.getCell(col);
				c.setCellValue(stat.toString());
				c.setCellStyle(csText);
			}
		}
		
		for (ModelStat stat : regPar.getModelStats()) {
			tmpRow = rows.containsKey(stat.getLabel()) ? rows.get(stat.getLabel()) : ++row;
			r = sh.getRow(tmpRow) != null ? sh.getRow(tmpRow) : sh.createRow(tmpRow);
			c = r.getCell(0);
			if (c.getCellType().equals(CellType.BLANK))
				c.setCellValue(stat.getLabel());
			c = r.getCell(col);
			c.setCellValue(stat.toString());
			c.setCellStyle(csText);
		}
		
		tmpRow = rows.containsKey("created") ? rows.get("created") : ++row;
		r = sh.getRow(tmpRow) != null ? sh.getRow(tmpRow) : sh.createRow(tmpRow);
		c = r.getCell(0);
		if (c.getCellType().equals(CellType.BLANK))
			c.setCellValue("created");
		c = r.getCell(col);
		c.setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		
		sh.autoSizeColumn(0);
		sh.autoSizeColumn(col);
	}
	
}