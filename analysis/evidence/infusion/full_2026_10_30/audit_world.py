import io,struct,zlib,gzip,json,sys,pathlib,uuid
class NBT:
 def __init__(self,data):self.f=io.BytesIO(data)
 def num(self,kind):return struct.unpack('>'+kind,self.f.read(struct.calcsize('>'+kind)))[0]
 def string(self):return self.f.read(self.num('H')).decode()
 def value(self,t):
  if t in (1,2,3,4,5,6):return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return list(self.f.read(self.num('i')))
  if t==8:return self.string()
  if t==9:
   kind=self.num('b');return [self.value(kind) for _ in range(self.num('i'))]
  if t==10:
   out={}
   while True:
    kind=self.num('b')
    if kind==0:return out
    key=self.string();out[key]=self.value(kind)
  if t in(11,12):return [self.num('i' if t==11 else 'q') for _ in range(self.num('i'))]
  raise ValueError(t)
 def root(self):kind=self.num('b');self.string();return self.value(kind)
world=pathlib.Path(sys.argv[1]);phase=sys.argv[2]
b=(world/'region/r.0.0.mca').read_bytes();offset=int.from_bytes(b[:3],'big')*4096;size=int.from_bytes(b[offset:offset+4],'big');compression=b[offset+4];raw=b[offset+5:offset+4+size]
raw={1:gzip.decompress,2:zlib.decompress,3:lambda x:x}[compression](raw)
chunk=NBT(raw).root();tiles={(t['x'],t['y'],t['z']):t for t in chunk['block_entities']}
center=tiles[8,126,8];matrix=tiles[8,128,8];sides=[tiles[5,126,8],tiles[11,126,8]];jars=[tiles[4,126,6],tiles[8,126,4],tiles[12,126,6]]
properties=dict(line.split('=',1) for line in (world/'infusion-paid-test.properties').read_text().splitlines() if line and not line.startswith('#'))
ritual=uuid.UUID(properties['ritual']);uid=[struct.unpack('>i',ritual.bytes[i:i+4])[0] for i in range(0,16,4)]
result={'phase':phase,'ritual':str(ritual),'matrix':matrix,'center':center,'sides':sides,'jars':jars}
# Registry-specific jar state is recorded verbatim for independent inspection.
if phase=='partial':
 ledger=matrix['PortInfusionLedger'];assert matrix['crafting']==1 and ledger['Format']==3 and ledger['Paid']==8 and ledger['RitualId']==uid
 assert ledger['PlannedOutput']['Items'][0]['id']=='thaumcraft:crystal_aer'
 assert all(len(t['Items'])==1 for t in [center,*sides])
 assert [j.get('Amount',0) for j in jars]==[2,10,5]
else:
 assert matrix['crafting']==0 and 'PortInfusionLedger' not in matrix
 assert [j.get('Amount',0) for j in jars]==[0,0,0]
 assert center['Items'][0]['id']=='thaumcraft:crystal_aer' and center['Items'][0]['count']==1
 assert center['PortInfusionOutputRitual']==uid
 for i,t in enumerate(sides):assert not t.get('Items',[]) and t['PortInfusionRemovalRitual']==uid and t['PortInfusionRemovalBit']==1<<i
print(json.dumps(result,ensure_ascii=False,indent=2))
