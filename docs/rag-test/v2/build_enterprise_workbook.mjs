import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {Workbook, SpreadsheetFile} from '@oai/artifact-tool';

const out = fileURLToPath(new URL('./enterprise-assets/04-enterprise-service-operations-register.xlsx', import.meta.url));
await fs.mkdir(fileURLToPath(new URL('./enterprise-assets/', import.meta.url)), {recursive: true});
const wb = Workbook.create();
const summary = wb.worksheets.add('运营总览');
const changes = wb.worksheets.add('变更台账');
const risks = wb.worksheets.add('风险与行动项');
const sla = wb.worksheets.add('服务等级');
const header = {
    fill: '#1F4D78',
    font: {bold: true, color: '#FFFFFF'},
    horizontalAlignment: 'center',
    verticalAlignment: 'center'
};
const subheader = {fill: '#E8EEF5', font: {bold: true, color: '#1F4D78'}, verticalAlignment: 'center'};

summary.getRange('A1:H1').merge();
summary.getRange('A1').values = [['Aether Enterprise Service Operations Register']];
summary.getRange('A1:H1').format = {
    fill: '#1F4D78',
    font: {bold: true, color: '#FFFFFF', size: 16},
    horizontalAlignment: 'center',
    verticalAlignment: 'center'
};
summary.getRange('A1:H1').format.rowHeight = 30;
summary.getRange('A3:B7').values = [
    ['Document owner', 'Service Operations'], ['Reporting period', '2026-08'], ['Classification', 'Internal'], ['Review cadence', 'Monthly'], ['Source of truth', 'Controlled operational register'],
];
summary.getRange('A3:A7').format = subheader;
summary.getRange('D3:H3').merge();
summary.getRange('D3').values = [['Management metrics']];
summary.getRange('D3:H3').format = header;
summary.getRange('D4:H7').values = [
    ['Metric', 'Target', 'Actual', 'Status', 'Evidence'],
    ['P1 first response', '≤ 30 min', 24, '=IF(F5<=30,"On target","Review")', 'INC-2026-0812'],
    ['P2 first response', '≤ 2 h', 105, '=IF(F6<=120,"On target","Review")', 'SUP-2026-4431'],
    ['Open high risks', '0', 2, '=IF(F7=0,"On target","Review")', 'Risk register'],
];
summary.getRange('D4:H4').format = subheader;
summary.getRange('F5:F6').format.numberFormat = '0 "min"';
summary.getRange('D4:H7').format.borders = {preset: 'all', style: 'thin', color: '#D9E2F0'};
summary.getRange('A1:H20').format.wrapText = true;
summary.getRange('A:H').format.columnWidth = 18;
summary.getRange('A1:H20').format.autofitRows();

changes.getRange('A1:I1').values = [['Change ID', 'Service', 'Classification', 'Planned window', 'Owner', 'Risk', 'Rollback owner', 'Approval status', 'Evidence']];
changes.getRange('A1:I1').format = header;
changes.getRange('A2:I6').values = [
    ['CHG-2026-0818', 'Knowledge retrieval', 'High', '2026-08-18 20:00', 'Platform Engineering', 'Vector index migration', 'SRE on-call', 'Approved', 'CAB-2026-218'],
    ['CHG-2026-0820', 'Identity service', 'Standard', '2026-08-20 21:00', 'Identity Team', 'Certificate rotation', 'Identity Team', 'Approved', 'Runbook IAM-12'],
    ['CHG-2026-0822', 'Billing API', 'Normal', '2026-08-22 20:00', 'API Platform', 'Rate-limit policy', 'API Platform', 'Pending', 'Peer review required'],
    ['CHG-2026-0826', 'Knowledge review', 'High', '2026-08-26 20:00', 'Knowledge Team', 'Review workflow schema', 'Knowledge Team', 'Approved', 'CAB-2026-225'],
    ['CHG-2026-0828', 'Webhook gateway', 'Normal', '2026-08-28 21:00', 'Integration Team', 'Signature validator', 'Integration Team', 'Approved', 'SEC-2026-091'],
];
changes.getRange('A1:I6').format.borders = {preset: 'all', style: 'thin', color: '#D9E2F0'};
changes.getRange('A1:I6').format.wrapText = true;
changes.getRange('A:I').format.columnWidth = 18;
changes.getRange('A1:I6').format.autofitRows();
changes.getRange('H2:H100').dataValidation = {
    rule: {
        type: 'list',
        values: ['Draft', 'Pending', 'Approved', 'Implemented', 'Rolled back']
    }
};

risks.getRange('A1:H1').values = [['Risk ID', 'Domain', 'Description', 'Likelihood', 'Impact', 'Owner', 'Due date', 'Treatment status']];
risks.getRange('A1:H1').format = header;
risks.getRange('A2:H7').values = [
    ['RSK-001', 'Identity', 'Legacy admin account lacks MFA evidence', 'Medium', 'High', 'Security Operations', '2026-08-20', 'In progress'],
    ['RSK-002', 'Data governance', 'Deprecated knowledge base has no retention owner', 'Low', 'High', 'Data Protection', '2026-08-24', 'Open'],
    ['RSK-003', 'RAG quality', 'New reranker lacks regression baseline', 'Medium', 'Medium', 'Model Owner', '2026-08-26', 'In progress'],
    ['RSK-004', 'Billing', 'Batch client retries without idempotency key', 'High', 'Medium', 'API Platform', '2026-08-18', 'Open'],
    ['RSK-005', 'Operations', 'P1 customer communication drill overdue', 'Low', 'Medium', 'Service Operations', '2026-08-30', 'Planned'],
    ['RSK-006', 'Vendor', 'Webhook signing secret rotation evidence missing', 'Medium', 'Medium', 'Integration Team', '2026-08-22', 'Open'],
];
risks.getRange('A1:H7').format.borders = {preset: 'all', style: 'thin', color: '#D9E2F0'};
risks.getRange('A:H').format.columnWidth = 20;
risks.getRange('A1:H7').format.wrapText = true;
risks.getRange('A1:H7').format.autofitRows();
risks.getRange('D2:D100').dataValidation = {rule: {type: 'list', values: ['Low', 'Medium', 'High']}};
risks.getRange('E2:E100').dataValidation = {rule: {type: 'list', values: ['Low', 'Medium', 'High']}};
risks.getRange('H2:H100').dataValidation = {
    rule: {
        type: 'list',
        values: ['Open', 'Planned', 'In progress', 'Accepted', 'Closed']
    }
};

sla.getRange('A1:F1').values = [['Support plan', 'P1 response', 'P2 response', 'Coverage', 'Communication', 'Escalation owner']];
sla.getRange('A1:F1').format = header;
sla.getRange('A2:F4').values = [['Standard', '4 business hours', '1 business day', 'Weekdays 09:00-18:00', 'Service desk', 'Support lead'], ['Business', '1 hour', '4 business hours', 'Weekdays 08:00-22:00', 'Service desk + CSM', 'Support lead'], ['Enterprise', '30 minutes', '2 hours', '24×7', 'Status page + named contacts', 'Incident commander']];
sla.getRange('A1:F4').format.borders = {preset: 'all', style: 'thin', color: '#D9E2F0'};
sla.getRange('A:F').format.columnWidth = 22;
sla.getRange('A1:F4').format.wrapText = true;
sla.getRange('A1:F4').format.autofitRows();
for (const sheet of [summary, changes, risks, sla]) {
    sheet.freezePanes.freezeRows(1);
    sheet.showGridLines = false;
}
const file = await SpreadsheetFile.exportXlsx(wb);
await file.save(out);
console.log(out);
