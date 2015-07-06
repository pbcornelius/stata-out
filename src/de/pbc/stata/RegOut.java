package de.pbc.stata;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.stata.sfi.Macro;
import com.stata.sfi.Matrix;
import com.stata.sfi.SFIToolkit;
import com.stata.sfi.Scalar;

import de.pbc.stata.Plugin;
import de.pbc.stata.StataUtils;

public class RegOut implements Plugin {
	
	// ENTRY POINT --------------------------------------------------- //
	
	public static int start(String[] args) throws Exception {
		return new RegOut().execute(args);
	}
	
	// PUBLIC ------------------------------------------------------- //
	
	@Override
	public int execute(String[] args) throws Exception {
		List<String> argsList = Arrays.asList(args);
		
		if (argsList.contains("e") || argsList.contains("excel"))
			return excelOut(argsList);
		
		else
			throw new Exception("no output specified");
	}
	
	// PRIVATE ------------------------------------------------------ //
	
	private int excelOut(List<String> args) throws Exception {
		if (Macro.getLocal("e_cmd") == null)
			throw new Exception("no estimation stored");
		
		List<Term> terms = Arrays.stream(Matrix.getMatrixColNames("e(b)"))
				.map(Term::create)
				.collect(Collectors.toList());
		double[][] table = StataUtils.getMatrix("r(table)");
		Function<Double, String> sigLevels = (p) -> p < .01 ? "***" : p < .05 ? "**" : p < .1 ? "*" : "";
		
		boolean merge = args.contains("m") || args.contains("merge");
		boolean silently = args.contains("s") || args.contains("silently");
		
		Path path = args.stream()
				.filter((a) -> a.startsWith("path="))
				.findFirst()
				.map((a) -> Paths.get(a.substring("path=".length())))
				.orElse(Paths.get("regOut.xlsx"));
		
		try (XSSFWorkbook wb = merge && Files.exists(path)
				? new XSSFWorkbook(Files.newInputStream(path))
				: new XSSFWorkbook()) {
			
			XSSFSheet sh = wb.createSheet(iterateSheetName(
					wb,
					WorkbookUtil.createSafeSheetName(Macro.getLocal("e_cmd").replaceAll("[\\s\\v]+", " ")),
					0));
			
			wb.setActiveSheet(wb.getSheetIndex(sh));
			wb.setSelectedTab(wb.getSheetIndex(sh));
			
			Row r = sh.createRow(0);
			r.createCell(0).setCellValue(
					Macro.getLocal("e_cmd").replaceAll("[\\s\\v]+", " ") + " ("
							+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + ")");
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
					r.createCell(1).setCellValue(
							BigDecimal.valueOf(table[0][i]).setScale(2, RoundingMode.HALF_UP)
									+ sigLevels.apply(table[3][i]));
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
				}
			}
			
			r = sh.createRow(terms.size() + 1);
			r.createCell(0).setCellValue("N");
			r.createCell(1).setCellValue(Scalar.getValue("es_N"));
			r.getCell(1).setCellStyle(cs0d);
			
			r = sh.createRow(terms.size() + 2);
			r.createCell(0).setCellValue("Groups");
			r.createCell(1).setCellValue(Scalar.getValue("es_N_g"));
			r.getCell(1).setCellStyle(cs0d);
			
			r = sh.createRow(terms.size() + 3);
			r.createCell(0).setCellValue("F-Stat");
			r.createCell(1).setCellValue(Scalar.getValue("es_F"));
			r.getCell(1).setCellStyle(cs2d);
			
			sh.autoSizeColumn(0);
			sh.autoSizeColumn(1);
			
			try (FileOutputStream out = new FileOutputStream(path.toFile())) {
				wb.write(out);
			}
			
			if (!silently)
				SFIToolkit.display("{browse \"" + path + "\":Open " + path + "}");
			
			return 0;
		} catch (Exception e) {
			SFIToolkit.error(SFIToolkit.stackTraceToString(e));
			return 45;
		}
	}
	
	private String iterateSheetName(Workbook wb, String name, int i) {
		String tmpName = i < 1 ? name : name + i;
		if (wb.getSheet(tmpName) == null)
			return tmpName;
		else
			return iterateSheetName(wb, name, i + 1);
	}
	
}