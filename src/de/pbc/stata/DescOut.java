package de.pbc.stata;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.stata.sfi.SFIToolkit;
import com.stata.sfi.Scalar;

public class DescOut {
	
	// VARIABLES ----------------------------------------------------- //
	
	private boolean merge, quietly;
	
	private String var;
	
	private XSSFWorkbook wb;
	
	// PUBLIC ------------------------------------------------------- //
	
	public int execute(String[] args) throws Exception {
		List<String> argsList = Arrays.asList(args).stream().map((s) -> s.toLowerCase()).collect(Collectors.toList());
		
		if (!argsList.stream().anyMatch((a) -> a.startsWith("var=")))
			throw new Exception("no variable specified");
		
		if (argsList.contains("e") || argsList.contains("excel"))
			return excelOut(argsList);
		else
			throw new Exception("no output specified");
	}
	
	// PRIVATE ------------------------------------------------------ //
	
	private int excelOut(List<String> args) {
		merge = args.contains("m") || args.contains("merge");
		quietly = args.contains("q") || args.contains("quietly");
		var = args.stream().filter((a) -> a.startsWith("var=")).findFirst().get();
		
		Path path = args.stream()
				.filter((a) -> a.startsWith("path="))
				.findFirst()
				.map((a) -> Paths.get(a.substring("path=".length())))
				.orElse(Paths.get("descOut.xlsx"));
		
		try (XSSFWorkbook wb = merge && Files.exists(path)
				? new XSSFWorkbook(Files.newInputStream(path))
				: new XSSFWorkbook()) {
			this.wb = wb;
			
			singlePage();
			
			try (FileOutputStream out = new FileOutputStream(path.toFile())) {
				wb.write(out);
			}
			
			if (!quietly)
				SFIToolkit.display("{browse \"" + path + "\":Open " + path + "}");
			
			return 0;
		} catch (Exception e) {
			SFIToolkit.error(SFIToolkit.stackTraceToString(e));
			return 45;
		}
	}
	
	private void singlePage() {
		wb.setMissingCellPolicy(Row.CREATE_NULL_AS_BLANK);
		XSSFSheet sh = Optional.ofNullable(wb.getSheet("Sheet0")).orElseGet(() -> wb.createSheet());
		wb.setActiveSheet(wb.getSheetIndex(sh));
		wb.setSelectedTab(wb.getSheetIndex(sh));
		
		XSSFRow r = Optional.ofNullable(sh.getRow(0)).orElseGet(() -> sh.createRow(0));
		
		XSSFCell c = r.getCell(0);
		if (c.getCellType() == Cell.CELL_TYPE_BLANK) {
			c.setCellValue("Variables");
			
			c = r.getCell(1);
			c.setCellValue("Mean");
			
			c = r.getCell(2);
			c.setCellValue("Std. Dev.");
			
			c = r.getCell(3);
			c.setCellValue("Min");
			
			c = r.getCell(4);
			c.setCellValue("Max");
		}
		
		fillSinglePage(sh, sh.getLastRowNum() + 1);
	}
	
	private void fillSinglePage(XSSFSheet sh, int row) {
		CellStyle cs2d = wb.createCellStyle();
		cs2d.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
		
		XSSFRow r = sh.createRow(row);
		
		XSSFCell c = r.getCell(0);
		c.setCellValue(new Variable(var.substring("var=".length())).getLabel());
		
		c = r.getCell(1);
		c.setCellValue(Scalar.getValue("rs_mean"));
		c.setCellStyle(cs2d);
		
		c = r.getCell(2);
		c.setCellValue(Scalar.getValue("rs_sd"));
		c.setCellStyle(cs2d);
		
		c = r.getCell(3);
		c.setCellValue(Scalar.getValue("rs_min"));
		c.setCellStyle(cs2d);
		
		c = r.getCell(4);
		c.setCellValue(Scalar.getValue("rs_max"));
		c.setCellStyle(cs2d);
		
		sh.autoSizeColumn(0);
		sh.autoSizeColumn(1);
		sh.autoSizeColumn(2);
		sh.autoSizeColumn(3);
		sh.autoSizeColumn(4);
	}
}