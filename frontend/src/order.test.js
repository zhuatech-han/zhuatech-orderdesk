// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { validQty, parsePrices } from "./order.js";
const head =
  "customerCode,sku,unitPrice,minQty,stepQty,maxQty,enabled,revision";
test("pack increments start at the minimum", () => {
  assert.equal(validQty(8, { minQty: 5, stepQty: 3, maxQty: 20 }), true);
  assert.equal(validQty(9, { minQty: 5, stepQty: 3, maxQty: 20 }), false);
  assert.equal(validQty(8.1, { minQty: 5, stepQty: 3, maxQty: 20 }), false);
});
test("CSV accepts BOM, CRLF, escaped quotes and a blank new revision", () => {
  const rows = parsePrices(
    "\uFEFF" + head + '\r\n"C","P","0.10",1,1,10,true,\r\n',
  );
  assert.equal(rows[0].unitPrice, "0.10");
  assert.equal(rows[0].revision, null);
  assert.equal(
    parsePrices(head + '\n"C""1",P,2.30,1,1,100,false,4')[0].customerCode,
    'C"1',
  );
});
test("malformed CSV and silently rounded prices are rejected", () => {
  for (const v of [
    head + "\nC,P,1.111,1,1,10,true,",
    head + '\n"C,P,1.00,1,1,10,true,',
    head + "\nC,P,1,1,1,10,TRUE,",
    head + "\nC,P,1,1,1,10,true,-1",
    head + "\nC,P,1,1,1,10,true,1,EXTRA",
  ])
    assert.throws(() => parsePrices(v));
});
test("CSV caps batch length and bytes before sending", () => {
  assert.throws(() =>
    parsePrices(head + "\n" + "C,P,1,1,1,10,true,\n".repeat(501)),
  );
  assert.throws(() => parsePrices("A".repeat(524289)), {
    message: "IMPORT_TOO_LARGE",
  });
});

test("CSV diagnostics identify the bad record and price reason without rounding it", () => {
  assert.throws(
    () =>
      parsePrices(
        head +
          "\nTEST-A,TEST-SKU-01,1.00,1,1,10,true,1\nTEST-A,TEST-SKU-02,1.111,1,1,10,true,1",
      ),
    (e) =>
      e.message === "INVALID_IMPORT" &&
      e.recordNumber === 2 &&
      e.detail[0].includes("两位小数"),
  );
});
