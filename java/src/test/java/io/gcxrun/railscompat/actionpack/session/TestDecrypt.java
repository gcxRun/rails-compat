package io.gcxrun.railscompat.actionpack.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class TestDecrypt {

  private static final String rubySessionCookieValue =
      "9w4LHq4WCaiutEyVGbgnXfBjaTKUJKmpADm%2BwvGyxfARpoqlx6DwOcDv%2BKlRGLSA5cejw4Pa2A7JKDCsOzz9"
          + "th1T09Yu255QLMEt7hveRlyuvx0Q%2BUZ8dZeAeUxLpYGjdoQvi%2FiFX2NCT8LjgF3SVMQ8aow3i9zfu0ZieqDzfdNCe4hygF3%2BhjGCphFp"
          + "4ncbYZPvaic709uTQuOpvocYiJp37OKFEt6Pwmx9lqSfJvJ1up8qcORbCMFTn%2BbVS3mIIRiKg%2FUeUWvzdVnPx%2F56NNg5Qg4ZI"
          + "xM1IW7uaHaYR5wIvD6eHbQNT%2FXySWuvJ%2BrZygFufyGKQKOofeszHp26fj%2FmjPCVYuMOClWZaRmKl%2FbdELKYICJSk41bs9Zy"
          + "rvpHyr0EgH%2FlW2lkyR1esnjUULy%2FKSL9giQQ%2Ft9yRzn1PVXCNdy5zNNjDgISyQyJbZgIpW3oJ9WgctiUkMYZMYo0PHXDhWg0E"
          + "DpR1%2FMU0%2BP09DLsWOaS7w5goITnxIflqKkqAMVsZBbRxbS3PICn9U18LaeqI3u4csVyLzya1p2FKVBYsf4liVaBCSkMvaOuW9aO"
          + "d64G5bGAN37QaufWCuCK%2BLdjG8xloGGrwDI8cImOivuC%2BLjLGF%2BmAQ9s57SIVxvHg636RH%2B3mOupQx7mqUgyZPJDGnkb%2FY"
          + "VvAq7%2F5xKnk9NoOBO5H%2BbqdLC3sVHWxJSvDPo0MH0W4l32L%2B9PJnoSqQ5dnW6dhbUnFR2pukdYNcMkiMVLfMdu%2BdbUW0ejT"
          + "RHgDDPdkgTWTF2%2B%2BZKYR%2BYGonqzoC1tVroKN7pExTMVrb1wn4lQOlNeRhjCpPs8wEC20WD9N3SaZ2u%2FvG6U5xF9ZhjM6mx"
          + "gylkvL5D367F3VeRfthXYmUFMBNboV%2FvV%2FWhvPvAvRq6AHr7qKwPX9mGVKwmxVw%2Bpx%2FjaBZ%2Fxh%2F8PbO3YJPTxgwq6"
          + "DhlFL%2BfUxb9K02YqvZKfV%2BVKMtYq5%2B2h1EQkeP5iaGSRH1gLJzF3no4bTp%2FTb1PQ5osBd9IdEA%2FMZA%2B5PxcrbpfY6"
          + "WzgErJ%2B61bOKLXM%2BjXGqnBVRctqMhi9002E8bAg24uxUWOdriDEanJ29Ijuvk14cZC7xX39O6yLG%2FeenksV9kCREjGLLEW7"
          + "ZFluiPMG8L4e8Jiu1jNMW3Pskbm925%2FSu6NP%2BMHCDxKxfoY2woV%2Bbm7W2wMeDOWB5xdlCjxuozEur2SrjZsp%2BIlsJOkPsY"
          + "9J3m0%2BBBLa7SuO6T8yt5fVKGDBXZKP3nsPn5RuMTcylPDaa9B7tUAJkTE1%2BtM%3D--5em3m%2FaYPiMqx6Gc--%2FdnwGtSn9B"
          + "2qt5BqdTGDJg%3D%3D";

  private static final String secretKeyBase =
      "6894a355142c571fc6d5c5bcfeb7e35c7b0e143d3c98277bc4111d04bd6aa249c6b0bca"
          + "97124d943e6eeaba1b5ee6d56d3b1b5a42502201b1b5d38e98de861ee";

  @Test
  public void testSessionCookeDecryption() {
    // set raw cookie value and base secret key
    var session = RubySession.fromCookieValue(rubySessionCookieValue, secretKeyBase);
    // decrypt the cookie and un marshal the ruby object in a map
    var hash = session.decrypt();

    // basic types are correctly decrypted
    assertNotNull(hash);
    assertEquals(12, hash.size());
    assertEquals("4PQf61nmurTL3ICmGUKwQ0YkdUw4qiWb6qUrLYVAiAQ=", hash.get("_csrf_token"));
    assertEquals("doctor", hash.get("account_type"));

    assertEquals(132138561L, hash.get("account_id"));
    assertEquals(1695905840L, hash.get("last_password_change_check_at"));
  }
}
