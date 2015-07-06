package de.pbc.stata;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.stata.sfi.Macro;
import com.stata.sfi.Matrix;
import com.stata.sfi.SFIToolkit;

import de.pbc.stata.Plugin;
import de.pbc.stata.StataUtils;

public class MarginsOut implements Plugin {
	
	// ENTRY POINT --------------------------------------------------- //
	
	public static int start(String[] args) {
		return new MarginsOut().marginsOut(args);
	}
	
	// PUBLIC ------------------------------------------------------- //
	
	@Override
	public int execute(String[] args) {
		return marginsOut(args);
	}
	
	// PRIVATE ------------------------------------------------------- //
	
	private int marginsOut(String[] args) {
		try {
			double[][] table = StataUtils.transposeMatrix(StataUtils.getMatrix("r(table)"));
			double[][] at = StataUtils.transposeMatrix(StataUtils.getMatrix("r(at)"));
			List<Integer> varyingVars = getVaryingVars(at);
			String[] varNames = Matrix.getMatrixColNames("r(at)");
			
			Path path = Paths.get("marginsOut.xlsx");
			
			try (Workbook wb = Files.exists(path) ? new XSSFWorkbook(Files.newInputStream(path)) : new XSSFWorkbook()) {
				Sheet sh = wb.createSheet();
				
				Row r = sh.createRow(0);
				r.createCell(0).setCellValue(Macro.getLocal("r_cmdline"));
				
				r = sh.createRow(1);
				for (int i = 0; i < varyingVars.size(); i++)
					r.createCell(i).setCellValue(varNames[varyingVars.get(i)]);
				r.createCell(varyingVars.size()).setCellValue("Margin");
				r.createCell(varyingVars.size() + 1).setCellValue("Std. Err.");
				r.createCell(varyingVars.size() + 2).setCellValue("z");
				r.createCell(varyingVars.size() + 3).setCellValue("P>|z|");
				
				for (int i = 0; i < table.length; i++) {
					r = sh.createRow(i + 2);
					for (int j = 0; j < varyingVars.size(); j++)
						r.createCell(j).setCellValue(at[varyingVars.get(j)][i]);
					for (int j = 0; j < 4; j++)
						r.createCell(j + varyingVars.size()).setCellValue(table[i][j]);
				}
				
				sh = wb.createSheet();
				
				r = sh.createRow(0);
				for (int i = 0; i < at.length; i++) {
					r.createCell(i).setCellValue(varNames[i]);
				}
				
				at = StataUtils.transposeMatrix(at);
				for (int i = 0; i < at.length; i++) {
					r = sh.createRow(i + 1);
					for (int j = 0; j < at[i].length; j++) {
						r.createCell(j).setCellValue(at[i][j]);
					}
				}
				
				try (FileOutputStream out = new FileOutputStream(path.toFile())) {
					wb.write(out);
				}
				
				SFIToolkit.display("{browse \"" + path + "\":Open " + path + "}");
			}
			
			return 0;
		} catch (Exception e) {
			SFIToolkit.error(SFIToolkit.stackTraceToString(e));
			return 45;
		}
	}
	
	private List<Integer> getVaryingVars(double[][] at) {
		List<Integer> varyingVars = new ArrayList<>();
		
		vars: for (int i = 0; i < at.length; i++) {
			for (int j = 1; j < at[i].length; j++) {
				if (at[i][j] != at[i][j - 1]) {
					varyingVars.add(i);
					continue vars;
				}
			}
		}
		
		return varyingVars;
	}
	
}