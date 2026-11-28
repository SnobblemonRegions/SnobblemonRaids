import hashlib,struct,unittest
from worldboss_journal_evidence import last_complete


class JournalEvidenceTest(unittest.TestCase):
    identity=b'i'*16
    def frame(self,number,previous=bytes(32),payload=b'payload'):
        body=struct.pack('>IIIQ',0x57424a31,1,len(payload),number)+self.identity+previous+payload
        digest=hashlib.sha256(body).digest()
        return body+digest+struct.pack('>I',0x57424531),digest
    def head(self,number,digest):
        body=struct.pack('>I',0x57425231)+self.identity+struct.pack('>Q',number)+digest
        return body+hashlib.sha256(body).digest()
    def test_new_complete_frame_after_older_head_is_retained(self):
        first,digest=self.frame(1);second,_=self.frame(2,digest,b'latest')
        result=last_complete(first+second,self.head(1,digest))
        self.assertEqual((result['sequence'],result['payload'],result['incomplete_tail_bytes']),(2,b'latest',0))
    def test_unacknowledged_partial_tail_keeps_complete_prefix(self):
        first,digest=self.frame(1);second,_=self.frame(2,digest)
        for tail in (second[:8],second[:-1]):
            result=last_complete(first+tail,self.head(1,digest))
            self.assertEqual(result['valid_bytes'],len(first));self.assertEqual(result['incomplete_tail_bytes'],len(tail))
    def test_acknowledged_missing_generation_is_rejected(self):
        first,digest=self.frame(1);_,next_digest=self.frame(2,digest)
        with self.assertRaises(ValueError):last_complete(first,self.head(2,next_digest))
    def test_complete_corruption_is_never_treated_as_partial(self):
        first,digest=self.frame(1);bad=bytearray(first);bad[68]^=1
        with self.assertRaises(ValueError):last_complete(bad,self.head(1,digest))
    def test_broken_chain_and_bad_head_are_rejected(self):
        first,digest=self.frame(1);second,_=self.frame(2,b'x'*32)
        with self.assertRaises(ValueError):last_complete(first+second,self.head(1,digest))
        bad=bytearray(self.head(1,digest));bad[-1]^=1
        with self.assertRaises(ValueError):last_complete(first,bad)


if __name__=='__main__':unittest.main()
