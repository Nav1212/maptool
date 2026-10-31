/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * MapTool Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.addon;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VersionRangeTest {
  @ParameterizedTest
  @CsvSource({
    "1.2.3,1.2.3,true",
    "1.2.3,1.2.4,false",
    "1,1.99.0,true",
    "1,2.0.0,false",
    "1.2.x,1.2.99,true",
    "1.2,1.3.0,false",
    "^1.2.3,1.99.0,true",
    "^1.2.3,2.0.0,false",
    "^0.2.3,0.2.9,true",
    "^0.2.3,0.3.0,false",
    "^0.0.3,0.0.4,false",
    "~1.2.3,1.2.99,true",
    "~1.2.3,1.3.0,false",
    ">=1.2.3 <2.0.0,1.9.0,true",
    ">= 1.2.3 < 2.0.0,2.0.0,false",
    "1.2.3 || >=2.0.0,2.1.0,true",
    "1.2.3 || >=2.0.0,1.9.0,false",
    "*,99.0.0,true",
    "*,1.0.0-beta,false",
    ">=1.2.3,1.3.0-beta,false",
    ">=1.2.3-alpha <2.0.0,1.2.3-beta,true",
    ">=1.2.3-alpha <2.0.0,1.3.0-beta,false",
    "1.2.3+build,1.2.3+other,true",
    "v1.2.3,v1.2.3,true"
  })
  void selectsSupportedVersions(String range, String version, boolean expected) {
    assertEquals(expected, VersionRange.parse(range).contains(version));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        " ",
        "01.2.3",
        "1.x.3",
        "1.2.3 - 2.0.0",
        "^1.2",
        "banana",
        "1.2.3 ||",
        ">=",
        "1.2.3.4",
        "1.2.3-01"
      })
  void rejectsUnsupportedOrMalformedRanges(String expression) {
    assertThrows(IllegalArgumentException.class, () -> VersionRange.parse(expression));
  }

  @Test
  void followsSemverPrereleaseOrderingAndUnlimitedNumericComponents() {
    var sequence =
        List.of(
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0");
    for (int i = 1; i < sequence.size(); i++) {
      assertTrue(
          SemanticVersion.parse(sequence.get(i - 1))
                  .compareTo(SemanticVersion.parse(sequence.get(i)))
              < 0);
    }
    assertTrue(
        VersionRange.parse("^999999999999999999999999.0.0")
            .contains("999999999999999999999999.999.0"));
    assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.2.3-01"));
  }
}
