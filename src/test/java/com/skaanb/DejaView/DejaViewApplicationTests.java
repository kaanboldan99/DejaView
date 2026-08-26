package com.skaanb.DejaView;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("DejaView - Tüm Entegrasyon ve Birim Testleri Süiti")
// Hangi paketin altındaki testlerin tetikleneceğini seçiyoruz
@SelectPackages("com.skaanb.DejaView")
public class DejaViewApplicationTests {
	// Bu sınıfın içi boş kalacak.
	// Üzerindeki @Suite ve @SelectPackages anotasyonları, bu sınıfı çalıştırdığınızda
	// altındaki tüm paketlerdeki (*Test.java) test sınıflarını otomatik tetikler.
}