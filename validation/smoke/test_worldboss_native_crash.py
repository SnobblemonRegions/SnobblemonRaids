import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from worldboss_native_crash import sdk_snapshot


class SdkEvidenceTest(unittest.TestCase):
    def test_missing_database_cannot_be_created_by_inspection(self):
        with tempfile.TemporaryDirectory() as folder:
            file=Path(folder)/'missing.db'
            with self.assertRaises(sqlite3.OperationalError):sdk_snapshot(file)
            self.assertFalse(file.exists())

    def test_reads_only_committed_profiles_and_receipts_without_waiting_for_writer(self):
        with tempfile.TemporaryDirectory() as folder:
            file=Path(folder)/'fixture.db'
            connection=sqlite3.connect(file)
            try:
                connection.execute('PRAGMA journal_mode=WAL')
                connection.execute('CREATE TABLE profiles(pokemon_id TEXT,body TEXT)')
                connection.execute('CREATE TABLE operations(operation_id TEXT,pokemon_id TEXT,kind TEXT)')
                connection.execute('INSERT INTO profiles VALUES(?,?)',('one',json.dumps({'revision':1})))
                connection.execute("INSERT INTO operations VALUES('op1','one','ACQUIRE')")
                connection.commit()
                connection.execute('INSERT INTO profiles VALUES(?,?)',('two',json.dumps({'revision':2})))
                connection.execute("INSERT INTO operations VALUES('op2','two','ACQUIRE')")
                self.assertEqual({'profiles':{'one':{'revision':1}},'acquisitions':{'op1':'one'}},sdk_snapshot(file))
                connection.commit()
                self.assertEqual(2,len(sdk_snapshot(file)['profiles']))
            finally:connection.close()
