package datingapp.storage.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapperHelperTest {

    enum TestEnum {
        VAL1,
        VAL2
    }

    /** A very simple manual mock for ResultSet to avoid Mockito dependency. */
    @SuppressWarnings("deprecation")
    static class MockResultSet implements ResultSet {
        private String value;

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public String getString(String columnLabel) throws SQLException {
            return value;
        }

        // --- All other methods are required but can throw UnsuppotedOperationException ---
        @Override
        public boolean next() throws SQLException {
            return false;
        }

        @Override
        public void close() throws SQLException {
            // Intentionally empty
        }

        @Override
        public boolean wasNull() throws SQLException {
            return value == null;
        }

        @Override
        public boolean getBoolean(int columnIndex) throws SQLException {
            return false;
        }

        @Override
        public byte getByte(int columnIndex) throws SQLException {
            return 0;
        }

        @Override
        public short getShort(int columnIndex) throws SQLException {
            return 0;
        }

        @Override
        public int getInt(int columnIndex) throws SQLException {
            return 0;
        }

        @Override
        public long getLong(int columnIndex) throws SQLException {
            return 0;
        }

        @Override
        public float getFloat(int columnIndex) throws SQLException {
            return 0;
        }

        @Override
        public double getDouble(int columnIndex) throws SQLException {
            return 0;
        }

        @Override
        public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
            return null;
        }

        @Override
        public byte[] getBytes(int columnIndex) throws SQLException {
            return new byte[0];
        }

        @Override
        public Date getDate(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public Time getTime(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public Timestamp getTimestamp(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public InputStream getAsciiStream(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public InputStream getUnicodeStream(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public InputStream getBinaryStream(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public String getString(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public boolean getBoolean(String columnLabel) throws SQLException {
            return false;
        }

        @Override
        public byte getByte(String columnLabel) throws SQLException {
            return 0;
        }

        @Override
        public short getShort(String columnLabel) throws SQLException {
            return 0;
        }

        @Override
        public int getInt(String columnLabel) throws SQLException {
            return 0;
        }

        @Override
        public long getLong(String columnLabel) throws SQLException {
            return 0;
        }

        @Override
        public float getFloat(String columnLabel) throws SQLException {
            return 0;
        }

        @Override
        public double getDouble(String columnLabel) throws SQLException {
            return 0;
        }

        @Override
        public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
            return null;
        }

        @Override
        public byte[] getBytes(String columnLabel) throws SQLException {
            return new byte[0];
        }

        @Override
        public Date getDate(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public Time getTime(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public Timestamp getTimestamp(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public InputStream getAsciiStream(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public InputStream getUnicodeStream(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public InputStream getBinaryStream(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public SQLWarning getWarnings() throws SQLException {
            return null;
        }

        @Override
        public void clearWarnings() throws SQLException {
            // Intentionally empty
        }

        @Override
        public String getCursorName() throws SQLException {
            return null;
        }

        @Override
        public ResultSetMetaData getMetaData() throws SQLException {
            return null;
        }

        @Override
        public Object getObject(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public Object getObject(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public int findColumn(String columnLabel) throws SQLException {
            return 0;
        }

        @Override
        public Reader getCharacterStream(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public Reader getCharacterStream(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public boolean isBeforeFirst() throws SQLException {
            return false;
        }

        @Override
        public boolean isAfterLast() throws SQLException {
            return false;
        }

        @Override
        public boolean isFirst() throws SQLException {
            return false;
        }

        @Override
        public boolean isLast() throws SQLException {
            return false;
        }

        @Override
        public void beforeFirst() throws SQLException {
            // Intentionally empty
        }

        @Override
        public void afterLast() throws SQLException {
            // Intentionally empty
        }

        @Override
        public boolean first() throws SQLException {
            return false;
        }

        @Override
        public boolean last() throws SQLException {
            return false;
        }

        @Override
        public int getRow() throws SQLException {
            return 0;
        }

        @Override
        public boolean absolute(int row) throws SQLException {
            return false;
        }

        @Override
        public boolean relative(int rows) throws SQLException {
            return false;
        }

        @Override
        public boolean previous() throws SQLException {
            return false;
        }

        @Override
        public void setFetchDirection(int direction) throws SQLException {
            // Intentionally empty
        }

        @Override
        public int getFetchDirection() throws SQLException {
            return 0;
        }

        @Override
        public void setFetchSize(int rows) throws SQLException {
            // Intentionally empty
        }

        @Override
        public int getFetchSize() throws SQLException {
            return 0;
        }

        @Override
        public int getType() throws SQLException {
            return 0;
        }

        @Override
        public int getConcurrency() throws SQLException {
            return 0;
        }

        @Override
        public boolean rowUpdated() throws SQLException {
            return false;
        }

        @Override
        public boolean rowInserted() throws SQLException {
            return false;
        }

        @Override
        public boolean rowDeleted() throws SQLException {
            return false;
        }

        @Override
        public void updateNull(int columnIndex) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBoolean(int columnIndex, boolean x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateByte(int columnIndex, byte x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateShort(int columnIndex, short x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateInt(int columnIndex, int x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateLong(int columnIndex, long x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateFloat(int columnIndex, float x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateDouble(int columnIndex, double x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateString(int columnIndex, String x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBytes(int columnIndex, byte[] x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateDate(int columnIndex, Date x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateTime(int columnIndex, Time x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateObject(int columnIndex, Object x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateNull(String columnLabel) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBoolean(String columnLabel, boolean x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateByte(String columnLabel, byte x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateShort(String columnLabel, short x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateInt(String columnLabel, int x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateLong(String columnLabel, long x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateFloat(String columnLabel, float x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateDouble(String columnLabel, double x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateString(String columnLabel, String x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBytes(String columnLabel, byte[] x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateDate(String columnLabel, Date x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateTime(String columnLabel, Time x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateCharacterStream(String columnLabel, Reader x, int length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateObject(String columnLabel, Object x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void insertRow() throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateRow() throws SQLException {
            // Intentionally empty
        }

        @Override
        public void deleteRow() throws SQLException {
            // Intentionally empty
        }

        @Override
        public void refreshRow() throws SQLException {
            // Intentionally empty
        }

        @Override
        public void cancelRowUpdates() throws SQLException {
            // Intentionally empty
        }

        @Override
        public void moveToInsertRow() throws SQLException {
            // Intentionally empty
        }

        @Override
        public void moveToCurrentRow() throws SQLException {
            // Intentionally empty
        }

        @Override
        public Statement getStatement() throws SQLException {
            return null;
        }

        @Override
        public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
            return null;
        }

        @Override
        public Ref getRef(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public Blob getBlob(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public Clob getClob(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public Array getArray(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
            return null;
        }

        @Override
        public Ref getRef(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public Blob getBlob(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public Clob getClob(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public Array getArray(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public Date getDate(int columnIndex, Calendar cal) throws SQLException {
            return null;
        }

        @Override
        public Date getDate(String columnLabel, Calendar cal) throws SQLException {
            return null;
        }

        @Override
        public Time getTime(int columnIndex, Calendar cal) throws SQLException {
            return null;
        }

        @Override
        public Time getTime(String columnLabel, Calendar cal) throws SQLException {
            return null;
        }

        @Override
        public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
            return null;
        }

        @Override
        public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
            return null;
        }

        @Override
        public URL getURL(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public URL getURL(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public void updateRef(int columnIndex, Ref x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateRef(String columnLabel, Ref x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBlob(int columnIndex, Blob x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBlob(String columnLabel, Blob x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateClob(int columnIndex, Clob x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateClob(String columnLabel, Clob x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateArray(int columnIndex, Array x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateArray(String columnLabel, Array x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public RowId getRowId(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public RowId getRowId(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public void updateRowId(int columnIndex, RowId x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateRowId(String columnLabel, RowId x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public int getHoldability() throws SQLException {
            return 0;
        }

        @Override
        public boolean isClosed() throws SQLException {
            return false;
        }

        @Override
        public void updateNString(int columnIndex, String nString) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateNString(String columnLabel, String nString) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
            // Intentionally empty
        }

        @Override
        public NClob getNClob(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public NClob getNClob(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public SQLXML getSQLXML(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public SQLXML getSQLXML(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
            // Intentionally empty
        }

        @Override
        public String getNString(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public String getNString(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public Reader getNCharacterStream(int columnIndex) throws SQLException {
            return null;
        }

        @Override
        public Reader getNCharacterStream(String columnLabel) throws SQLException {
            return null;
        }

        @Override
        public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateNCharacterStream(String columnLabel, Reader x, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateCharacterStream(String columnLabel, Reader x, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateNCharacterStream(String columnLabel, Reader x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateCharacterStream(String columnLabel, Reader x) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateClob(int columnIndex, Reader reader) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateClob(String columnLabel, Reader reader) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateNClob(int columnIndex, Reader reader) throws SQLException {
            // Intentionally empty
        }

        @Override
        public void updateNClob(String columnLabel, Reader reader) throws SQLException {
            // Intentionally empty
        }

        @Override
        public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
            return null;
        }

        @Override
        public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }
    }

    @Test
    void testReadEnumValid() throws SQLException {
        MockResultSet rs = new MockResultSet();
        rs.setValue("VAL1");

        TestEnum result = MapperHelper.readEnum(rs, "col", TestEnum.class);
        assertEquals(TestEnum.VAL1, result);
    }

    @Test
    void testReadEnumInvalid() throws SQLException {
        MockResultSet rs = new MockResultSet();
        rs.setValue("INVALID");

        TestEnum result = MapperHelper.readEnum(rs, "col", TestEnum.class);
        assertNull(result);
    }

    @Test
    void testReadEnumNull() throws SQLException {
        MockResultSet rs = new MockResultSet();
        rs.setValue(null);

        TestEnum result = MapperHelper.readEnum(rs, "col", TestEnum.class);
        assertNull(result);
    }

    @Test
    void testReadCsvAsList() throws SQLException {
        MockResultSet rs = new MockResultSet();
        rs.setValue("A, B, , C ");

        List<String> result = MapperHelper.readCsvAsList(rs, "col");
        assertEquals(List.of("A", "B", "C"), result);

        // Verify mutability
        result.add("D");
        assertTrue(result.contains("D"));
    }

    @Test
    void testReadCsvAsListEmpty() throws SQLException {
        MockResultSet rs = new MockResultSet();
        rs.setValue("  ");

        List<String> result = MapperHelper.readCsvAsList(rs, "col");
        assertTrue(result.isEmpty());

        // Verify mutability
        result.add("A");
        assertEquals(1, result.size());
    }
}
